FROM node:22-alpine

ENV TZ=UTC
WORKDIR /app

COPY package.json server.js index.html ./
RUN chown -R node:node /app

USER node
EXPOSE 9000
HEALTHCHECK --interval=10s --timeout=3s CMD wget -q -O /dev/null http://127.0.0.1:9000/healthz || exit 1

CMD ["node", "server.js"]
