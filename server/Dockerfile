FROM node:20-bookworm-slim
WORKDIR /app
COPY package.json package-lock.json ./
COPY server/package.json ./server/package.json
RUN npm ci --workspace=@idr/server --omit=dev --ignore-scripts
COPY server/src ./server/src
RUN mkdir -p /data/uploads && chown -R node:node /data /app
ENV NODE_ENV=production PORT=3001 UPLOAD_DIR=/data/uploads
USER node
EXPOSE 3001
HEALTHCHECK --interval=30s --timeout=5s CMD node -e "fetch('http://127.0.0.1:3001/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "server/src/index.js"]
