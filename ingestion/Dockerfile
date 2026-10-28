FROM python:3.13-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY ingestion.py .

USER 10001:10001
CMD ["python", "-u", "ingestion.py"]
