# Doon Public School, Etah — Free Cloudflare Edge Backend & Web Portal

This backend runs on **Cloudflare Workers + Cloudflare D1 (SQLite) + Cloudflare Assets**, comfortably handling **3,000+ daily logins and 30–500+ active concurrent users on Cloudflare's $0/month Free Tier**:

- **Cloudflare Workers:** 100,000 free requests/day
- **Cloudflare D1:** **5,000,000 free row reads/day** & 100,000 free row writes/day
- **Cloudflare Assets / Pages:** Unlimited free static website bandwidth

## 1-Minute Deploy Guide

```bash
cd cloudflare-backend
npm install

# 1. Log in to Cloudflare (free account)
npx wrangler login

# 2. Create the D1 SQLite database and copy the printed database_id into wrangler.toml
npx wrangler d1 create doon-school-db

# 3. Initialize all tables and indexes in D1
npx wrangler d1 execute doon-school-db --remote --file=./schema.sql

# 4. Set a strong secret key for JWT session signing
npx wrangler secret put JWT_SECRET

# 5. Deploy BOTH the Web Portal AND the API Worker in one command!
npx wrangler deploy
```

## Connect the Android App to Your Live Cloudflare URL
Once deployed, Wrangler prints your live URL (e.g. `https://doon-public-school-etah.<your-subdomain>.workers.dev`).
1. Open **AI Studio Secrets** (or `.env`) and set:
   `CLOUDFLARE_API_URL=https://doon-public-school-etah.<your-subdomain>.workers.dev`
2. Rebuild the Android APK — both the **Website** and the **Android App** will now sync live against the same Cloudflare D1 database!
