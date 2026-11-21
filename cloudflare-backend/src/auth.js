// Web Crypto PBKDF2-SHA256 Password Hashing & HMAC-SHA256 JWT Helpers for Cloudflare Workers

const encoder = new TextEncoder();
const decoder = new TextDecoder();

// Ephemeral fallback key per Worker isolate if JWT_SECRET hasn't been configured via `wrangler secret put JWT_SECRET`
const EPHEMERAL_FALLBACK_KEY = crypto.randomUUID() + "-" + crypto.randomUUID();

function bytesToBase64Url(bytes) {
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlToBytes(str) {
  const padded = str.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((str.length + 3) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function constantTimeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

export async function hashPassword(password) {
  const saltBytes = crypto.getRandomValues(new Uint8Array(16));
  const keyMaterial = await crypto.subtle.importKey(
    "raw",
    encoder.encode(password),
    { name: "PBKDF2" },
    false,
    ["deriveBits"]
  );
  const derived = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      salt: saltBytes,
      iterations: 100000,
      hash: "SHA-256",
    },
    keyMaterial,
    256
  );
  return `pbkdf2$${bytesToBase64Url(saltBytes)}$${bytesToBase64Url(new Uint8Array(derived))}`;
}

export async function verifyPassword(password, storedHash) {
  if (typeof storedHash !== "string" || !storedHash.startsWith("pbkdf2$")) {
    return false;
  }
  const parts = storedHash.split("$");
  if (parts.length !== 3) return false;
  const saltBytes = base64UrlToBytes(parts[1]);
  const expectedHash = parts[2];

  const keyMaterial = await crypto.subtle.importKey(
    "raw",
    encoder.encode(password),
    { name: "PBKDF2" },
    false,
    ["deriveBits"]
  );
  const derived = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      salt: saltBytes,
      iterations: 100000,
      hash: "SHA-256",
    },
    keyMaterial,
    256
  );
  const actualHash = bytesToBase64Url(new Uint8Array(derived));
  return constantTimeEqual(actualHash, expectedHash);
}

async function importHmacKey(secret) {
  const resolved = (secret && String(secret).trim()) || EPHEMERAL_FALLBACK_KEY;
  return crypto.subtle.importKey(
    "raw",
    encoder.encode(resolved),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign", "verify"]
  );
}

export async function signJwt(payload, secret, ttlSeconds = 60 * 60 * 24 * 7) {
  const header = { alg: "HS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const fullPayload = { ...payload, iat: now, exp: now + ttlSeconds };
  const encodedHeader = bytesToBase64Url(encoder.encode(JSON.stringify(header)));
  const encodedPayload = bytesToBase64Url(encoder.encode(JSON.stringify(fullPayload)));
  const signingInput = `${encodedHeader}.${encodedPayload}`;

  const key = await importHmacKey(secret);
  const sig = await crypto.subtle.sign("HMAC", key, encoder.encode(signingInput));
  return `${signingInput}.${bytesToBase64Url(new Uint8Array(sig))}`;
}

export async function verifyJwt(token, secret) {
  if (!token || typeof token !== "string") return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [encodedHeader, encodedPayload, encodedSig] = parts;
  const signingInput = `${encodedHeader}.${encodedPayload}`;

  try {
    const key = await importHmacKey(secret);
    const sigBytes = base64UrlToBytes(encodedSig);
    const valid = await crypto.subtle.verify("HMAC", key, sigBytes, encoder.encode(signingInput));
    if (!valid) return null;

    const payload = JSON.parse(decoder.decode(base64UrlToBytes(encodedPayload)));
    const now = Math.floor(Date.now() / 1000);
    if (!payload || typeof payload.exp !== "number" || payload.exp < now) {
      return null;
    }
    return payload;
  } catch {
    return null;
  }
}
