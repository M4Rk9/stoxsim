FROM node:24-alpine AS build
WORKDIR /app
ARG NEXT_PUBLIC_API_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production
ENV HOSTNAME=0.0.0.0
ENV PORT=3000
RUN addgroup -S stoxsim && adduser -S stoxsim -G stoxsim
# Next traces the required production modules into standalone.
COPY --from=build --chown=stoxsim:stoxsim /app/.next/standalone ./
COPY --from=build --chown=stoxsim:stoxsim /app/.next/static ./.next/static
COPY --from=build --chown=stoxsim:stoxsim /app/public ./public
USER stoxsim
EXPOSE 3000
CMD ["node", "server.js"]
