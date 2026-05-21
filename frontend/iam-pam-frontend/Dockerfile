# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM node:18-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci --quiet --legacy-peer-deps

COPY . .
RUN npm run build -- --configuration production

# ── Stage 2: Serve with nginx ─────────────────────────────────────────────────
FROM nginx:1.25-alpine
COPY --from=builder /app/dist/iam-pam-frontend /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
