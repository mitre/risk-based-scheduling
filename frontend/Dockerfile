FROM node:18

RUN mkdir /frontend
WORKDIR /frontend

COPY package*.json ./

RUN npm install

COPY . .


RUN npm run build
EXPOSE 3000
CMD npm run preview