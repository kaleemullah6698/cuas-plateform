# Operator console — ARCHITECTURE.md section 8.2
FROM node:22-alpine AS build
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
