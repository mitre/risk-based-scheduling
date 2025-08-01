FROM gradle:7.4.2-jdk17

RUN apt update
RUN apt-get install -y python3-pip
RUN apt install python-is-python3
RUN pip install mlflow==2.1.1 mlflow-export-import

RUN mkdir /app
COPY . /app
WORKDIR /app

RUN gradle fatJar

CMD java -jar "./build/libs/risk_based_scheduler-1.0-SNAPSHOT.jar"
