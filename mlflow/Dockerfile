FROM python:3.9

RUN pip install mlflow==2.1.1 psycopg2==2.9.5

CMD mlflow server --host 0.0.0.0 --port $MLFLOW_PORT --backend-store-uri postgresql://$DB_USER:$DB_PASSWORD@$DB_HOST:5432/$DB_NAME --default-artifact-root $MLFLOW_ARTIFACTS