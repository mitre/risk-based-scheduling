FROM python:3.9

RUN mkdir /case_gen
COPY . /case_gen
WORKDIR /case_gen

RUN pip install -r requirements.txt

CMD flask --app src/app.py run --host 0.0.0.0 --port 6500
