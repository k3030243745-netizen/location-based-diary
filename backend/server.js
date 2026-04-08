require("dotenv").config({ path: __dirname + "/.env" });

const express = require("express");
const cors = require("cors");
const { Pool } = require("pg");

console.log("cwd =", process.cwd());
console.log("DB_HOST =", process.env.DB_HOST);
console.log("DB_USER =", process.env.DB_USER);
console.log("DB_PASSWORD =", process.env.DB_PASSWORD);

const app = express();
app.use(cors());
app.use(express.json());

const pool = new Pool({
  host: process.env.DB_HOST,
  port: Number(process.env.DB_PORT || 5432),
  database: process.env.DB_NAME,
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  ssl: false
});
 
// ===== Intent classification configuration (Phase 1: Fixed mapping, no LLM involved)=====
const CATEGORY_DEFS = {
  retail_food_multiitem: {
    displayName: "Food & groceries retail",
    recommendedRadiusM: 600,
    classes: ["0699", "0819", "0669", "0768"]
    // 0699 Convenience stores and independent food stores
    // 0819 Supermarket chains
    // 0669 Grocers, greengrocers and fruiterers
    // 0768 Cash and carry
  },
  sport_complex: {
    displayName: "Gym / leisure centre",
    recommendedRadiusM: 900,
    classes: ["0293"]
    // Gymnasiums, sports halls and leisure centres
  },
  printing_photocopying: {
    displayName: "Printing & photocopying",
    recommendedRadiusM: 1200,
    classes: ["0130"]
  },
  cash_machines: {
    displayName: "Cash machine / bank",
    recommendedRadiusM: 600,
    classes: ["0141", "0138"]
    // 0141 Cash machines
    // 0138 Banks and building societies
  },
  cafes: {
    displayName: "Cafe / coffee",
    recommendedRadiusM: 400,
    classes: ["0013", "0798"]
    // 0013 Cafes, snack bars, tea rooms
    // 0798 Tea and coffee merchants
  },
  post_offices: {
    displayName: "Post office / parcel",
    recommendedRadiusM: 1500,
    classes: ["0763"]
  },
  chemists_pharmacies: {
    displayName: "Chemist / pharmacy",
    recommendedRadiusM: 800,
    classes: ["0364"]
  },
  restaurants: {
    displayName: "Restaurant / lunch",
    recommendedRadiusM: 700,
    classes: ["0043", "0013"]
    // 0043 Restaurants
    // 0013 Cafes (兜底)
  },
  public_transport: {
    displayName: "Public transport",
    recommendedRadiusM: 800,
    classes: ["0732", "0731", "0738", "0756", "0761"]
    // 0732 Bus stops
    // 0731 Bus and coach stations
    // 0738 Railway stations and areas beneath railway lines
    // 0756 Tram, metro and light railway stations
    // 0761 Underground stations
  }
};

// ===== purpose -> category Fixed rules =====
function classifyPurposeRule(purposeRaw) {
  const p = String(purposeRaw || "").trim().toLowerCase();

  if (/(milk|grocer|grocery|supermarket|shop|shopping)/.test(p)) {
    return {
      topCategories: [
        { categoryId: "retail_food_multiitem", confidence: 0.85 },
        { categoryId: "cafes", confidence: 0.20 }
      ],
      recommendedRadiusM: p.includes("milk") ? 400 : 600,
      keywords: ["groceries", "food"],
      source: "rule"
    };
  }

  if (/(gym|workout|fitness|leisure)/.test(p)) {
    return {
      topCategories: [
        { categoryId: "sport_complex", confidence: 0.90 }
      ],
      recommendedRadiusM: 900,
      keywords: ["gym", "fitness"],
      source: "rule"
    };
  }

  if (/(print|photocopy|copy|scan|document)/.test(p)) {
    return {
      topCategories: [
        { categoryId: "printing_photocopying", confidence: 0.90 }
      ],
      recommendedRadiusM: 1200,
      keywords: ["print", "copy"],
      source: "rule"
    };
  }

  if (/(cash|withdraw|atm|money)/.test(p)) {
    return {
      topCategories: [
        { categoryId: "cash_machines", confidence: 0.90 }
      ],
      recommendedRadiusM: 600,
      keywords: ["cash", "atm"],
      source: "rule"
    };
  }

  if (/(coffee|cafe|latte|espresso)/.test(p)) {
    return {
      topCategories: [
        { categoryId: "cafes", confidence: 0.90 }
      ],
      recommendedRadiusM: 400,
      keywords: ["coffee"],
      source: "rule"
    };
  }

  if (/(parcel|post|pickup|pick up|collection)/.test(p)) {
    return {
      topCategories: [
        { categoryId: "post_offices", confidence: 0.85 }
      ],
      recommendedRadiusM: 1500,
      keywords: ["parcel", "post"],
      source: "rule"
    };
  }

  if (/(medicine|meds|pharmacy|chemist|drug)/.test(p)) {
    return {
      topCategories: [
        { categoryId: "chemists_pharmacies", confidence: 0.90 }
      ],
      recommendedRadiusM: 800,
      keywords: ["medicine", "pharmacy"],
      source: "rule"
    };
  }

  if (/(lunch|eat|restaurant|dinner|food)/.test(p)) {
    return {
      topCategories: [
        { categoryId: "restaurants", confidence: 0.80 },
        { categoryId: "cafes", confidence: 0.55 }
      ],
      recommendedRadiusM: 700,
      keywords: ["lunch", "restaurant"],
      source: "rule"
    };
  }

  if (/(bus|train|station|tram|metro|underground|tube)/.test(p)) {
    return {
      topCategories: [
        { categoryId: "public_transport", confidence: 0.90 }
      ],
      recommendedRadiusM: 800,
      keywords: ["transport", "station"],
      source: "rule"
    };
  }

  // fallback
  return {
    topCategories: [
      { categoryId: "retail_food_multiitem", confidence: 0.25 },
      { categoryId: "restaurants", confidence: 0.20 },
      { categoryId: "public_transport", confidence: 0.15 }
    ],
    recommendedRadiusM: 600,
    keywords: [],
    source: "fallback"
  };
}

// ===== Health Check Interface =====
app.get("/health", async (req, res) => {
  try {
    const result = await pool.query("SELECT 1 AS ok");
    res.json({ ok: true, db: result.rows[0].ok });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// ===== purpose classify Interface =====
app.get("/intent/classify", async (req, res) => {
  try {
    const purpose = req.query.purpose;

    if (!purpose || String(purpose).trim().length === 0) {
      return res.status(400).json({ error: "purpose required" });
    }

    const r = classifyPurposeRule(purpose);

    const topCategories = (r.topCategories || []).slice(0, 3).map((c) => ({
      categoryId: c.categoryId,
      confidence: c.confidence,
      displayName: CATEGORY_DEFS[c.categoryId]?.displayName || c.categoryId
    }));

    res.json({
      purpose: String(purpose),
      topCategories,
      recommendedRadiusM: r.recommendedRadiusM,
      keywords: r.keywords || [],
      source: r.source || "rule"
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: err.message });
  }
});

// ===== Nearby POI Query Interface =====
app.get("/pois/near", async (req, res) => {
  try {
    const lat = parseFloat(req.query.lat);
    const lng = parseFloat(req.query.lng);
    const radius = parseInt(req.query.radius) || 300;
    const limit = parseInt(req.query.limit) || 20;

    const classesPrefix = req.query.classesPrefix;
    const classIn = req.query.classIn;
    const categoryId = req.query.categoryId;

    if (Number.isNaN(lat) || Number.isNaN(lng)) {
      return res.status(400).json({ error: "lat and lng required" });
    }

    let whereExtra = "";
    const params = [lng, lat, radius, limit];
    let pIndex = 5;

    // Perform prefix matching based on the last 4 digits.
    if (classesPrefix) {
      whereExtra += ` AND RIGHT(p.pointx_class, 4) LIKE $${pIndex++} `;
      params.push(classesPrefix + "%");
    }

    // classIn
    if (classIn) {
      const list = String(classIn)
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);

      if (list.length > 0) {
        whereExtra += ` AND RIGHT(p.pointx_class, 4) = ANY($${pIndex++}) `;
        params.push(list);
      }
    } else if (categoryId && CATEGORY_DEFS[categoryId]) {
      const list = CATEGORY_DEFS[categoryId].classes || [];
      if (list.length > 0) {
        whereExtra += ` AND RIGHT(p.pointx_class, 4) = ANY($${pIndex++}) `;
        params.push(list);
      }
    }

    const sql = `
      WITH q AS (
        SELECT ST_Transform(
          ST_SetSRID(ST_MakePoint($1, $2), 4326),
          27700
        ) AS pt
      )
      SELECT
        p.name,
        p.classname,
        p.pointx_class,
        p.street_name,
        p.postcode,
        ST_Y(ST_Transform(p.geom, 4326)) AS lat,
        ST_X(ST_Transform(p.geom, 4326)) AS lng,
        ST_Distance(p.geom, q.pt) AS distance_m
      FROM poi.poi p
      CROSS JOIN q
      WHERE ST_DWithin(p.geom, q.pt, $3)
        ${whereExtra}
      ORDER BY distance_m
      LIMIT $4
    `;

    const result = await pool.query(sql, params);
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: err.message });
  }
});

// ===== Start the server=====
const PORT = 8080;
app.listen(PORT, () => {
  console.log(`Server running at http://localhost:${PORT}`);
});
