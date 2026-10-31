# Build the static SPA, then serve it with nginx, which also forwards /api to the backend.
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Optional: a public demo deployment shows a "Fill in the demo account" button on the login page.
ARG VITE_DEMO_EMAIL=""
ARG VITE_DEMO_PASSWORD=""
RUN VITE_DEMO_EMAIL="$VITE_DEMO_EMAIL" VITE_DEMO_PASSWORD="$VITE_DEMO_PASSWORD" npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
