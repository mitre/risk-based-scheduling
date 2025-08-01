FROM python:3.9

RUN mkdir /sched
COPY . /sched
WORKDIR /sched

RUN pip install -r requirements.txt

CMD flask --app src/app.py run --host 0.0.0.0 --port 4000
