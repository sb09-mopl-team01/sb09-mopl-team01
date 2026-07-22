import os
import json
import boto3
import psycopg2
import psycopg2.extras
from requests_aws4auth import AWS4Auth
from opensearchpy import OpenSearch, RequestsHttpConnection

SECRET_NAME = os.environ.get('SECRET_NAME', 'mopl-prod-secrets')
REGION_NAME = os.environ.get('AWS_REGION', 'ap-northeast-2')

def get_secret():
    session = boto3.session.Session()
    client = session.client(service_name='secretsmanager', region_name=REGION_NAME)
    response = client.get_secret_value(SecretId=SECRET_NAME)
    if 'SecretString' in response:
        return json.loads(response['SecretString'])
    else:
        import base64
        return json.loads(base64.b64decode(response['SecretBinary']))

def lambda_handler(event, context):
    secrets = get_secret()

    os_host_raw = secrets['OPENSEARCH_URI']
    clean_os_host = os_host_raw.replace('https://', '').rstrip('/')

    # AWS IAM 인증(SigV4) 설정
    region = "ap-northeast-2"
    service = "es"
    credentials = boto3.Session().get_credentials()
    awsauth = AWS4Auth(
        credentials.access_key,
        credentials.secret_key,
        region,
        service,
        session_token=credentials.token
    )

    client = OpenSearch(
        hosts=[{'host': clean_os_host, 'port': 443}],
        http_auth=awsauth,
        use_ssl=True,
        verify_certs=True,
        connection_class=RequestsHttpConnection,
        timeout=600
    )

    batch_size = 1000
    offset = 0
    total_synced = 0

    conn = None
    try:
        conn = psycopg2.connect(
            host=secrets['RDS_HOST'],
            database=secrets['RDS_DB_NAME'],
            user=secrets['RDS_USERNAME'],
            password=secrets['RDS_PASSWORD'],
            port=int(secrets.get('RDS_PORT', 5432))
        )

        with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cursor:
            while True:
                cursor.execute("""
                    SELECT
                        c.id,
                        c.title,
                        c.description,
                        c.type,
                        c.created_at,
                        c.average_rating,
                        array_remove(array_agg(ct.tag), NULL) as tags
                    FROM contents c
                    LEFT JOIN content_tags ct ON c.id = ct.content_id
                    GROUP BY c.id
                    ORDER BY c.id
                    LIMIT %s OFFSET %s
                """, (batch_size, offset))

                rows = cursor.fetchall()
                if not rows:
                    break

                bulk_body = []
                for row in rows:
                    action = {
                        "_index": "contents",
                        "_id": str(row['id'])
                    }
                    bulk_body.append({"index": action})

                    doc = {
                        "id": str(row['id']),
                        "title": row['title'],
                        "description": row['description'],
                        "type": str(row['type']),
                        "tags": row['tags'] if row['tags'] else [],
                        "createdAt": str(row['created_at']) if row['created_at'] else None,
                        "averageRating": float(row['average_rating']) if row['average_rating'] is not None else 0.0
                    }
                    bulk_body.append(doc)

                client.bulk(body=bulk_body)

                total_synced += len(rows)
                offset += batch_size
                print(f"진행 중: {total_synced}건 콘텐츠 동기화 완료...")

    finally:
        if conn:
            conn.close()

    return {
        "statusCode": 200,
        "body": f"총 {total_synced}개의 콘텐츠 데이터가 DB에서 OpenSearch로 안전하게 동기화되었습니다."
    }