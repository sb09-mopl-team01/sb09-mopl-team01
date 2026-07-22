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

    role_arn = "arn:aws:iam::945504686053:role/service-role/rds-to-opensearch-sync-role-zsy6dnev"
    mapping_body = {
        "backend_roles": [role_arn],
        "hosts": [],
        "users": []
    }
    try:
        response = client.transport.perform_request(
            'PUT',
            '/_plugins/_security/api/rolesmapping/all_access',
            body=mapping_body
        )
        print("권한 매핑 성공:", response)
    except Exception as e:
        print("권한 매핑 안내 (이미 매핑되었거나 무시 가능):", e)

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
                    SELECT id, email, name, role, locked, created_at
                    FROM users
                    LIMIT %s OFFSET %s
                """, (batch_size, offset))

                rows = cursor.fetchall()
                if not rows:
                    break

                bulk_body = []
                for row in rows:
                    action = {
                        "_index": "users",
                        "_id": str(row['id'])
                    }
                    bulk_body.append({"index": action})

                    doc = {
                        "id": str(row['id']),
                        "name": row['name'],
                        "email": row['email'],
                        "role": row['role'],
                        "isLocked": bool(row['locked']),
                        "createdAt": str(row['created_at']) if row['created_at'] else None
                    }
                    bulk_body.append(doc)

                client.bulk(body=bulk_body)

                total_synced += len(rows)
                offset += batch_size
                print(f"진행 중: {total_synced}건 동기화 완료...")

    finally:
        if conn:
            conn.close()

    return {
        "statusCode": 200,
        "body": f"총 {total_synced}명의 유저 데이터가 DB에서 OpenSearch로 안전하게 동기화되었습니다."
    }