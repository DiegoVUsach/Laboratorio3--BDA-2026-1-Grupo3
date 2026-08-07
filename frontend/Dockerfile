# Paso 1: Build
FROM node:20-alpine AS build-stage

WORKDIR /app

# Copiamos dependencias
COPY package*.json ./
RUN npm install

# Copiamos el resto del proyecto
COPY . .

# Build de Vite
RUN npm run build


# Paso 2: Producción (Nginx)
FROM nginx:stable-alpine

# Asegúrate de que el nombre de la carpeta coincida con lo que genera 'npm run build'
COPY --from=build-stage /app/dist /usr/share/nginx/html

# Configuración de Nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]