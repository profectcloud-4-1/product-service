# Kubernetes - Local 구동

## 1. 이미지 준비

docker를 통해 로컬환경에 이미지를 빌드합니다.
빌드한 이미지 이름 및 태그를 아래에서 환경변수로 입력합니다.

## 2. .env 준비

`.env.template` 파일이 최신의 환경변수 항목들입니다.
각자의 로컬환경에 맞게 값을 채워넣고, 아래 항목들은 안내에 따라 채워주세요.

- `NAMESPACE`: 채우지 않아도 무관합니다.
- `IMAGE`: 방금 빌드한 이미지의 이름+태그를 채워넣습니다.
- `DB_URL`: 컨테이너 내부에서 실행되는 스프링부트에는 localhost=컨테이너가 됩니다. 컨테이너가 아닌 호스트로 연결될 수 있도록, `localhost` 대신 `host.docker.internal`을 입력합니다.
- `..._SERVICE_URL`: host에 타 서비스 **POD로 연결되는 Service리소스의 이름**을 적습니다. (http://user:8080, http://product:8080 등)

## 3. apply

- `k8s/apply.sh` 스크립트를 실행하면 필요한 리소스들이 쿠버네티스 클러스터에 등록/구동됩니다.

> Windows 환경은 `k8s/apply.bat`을 실행합니다.

## 4. 구동 확인

터미널을 두개 준비합니다.
한 터미널에서 아래 명령을 실행합니다.

```bash
kubectl port-forward svc/product 8080:8080
```

이후 다른 터미널에서 curl 명령을 실행하거나 브라우저를 통해 다음 요청을 보내봅니다.

```bash
curl http://localhost:8080/actuator/health
```
