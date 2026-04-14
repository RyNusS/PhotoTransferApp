# Claude CI 연동 가이드

이 파일은 Claude(Cowork)가 GitHub Actions를 직접 트리거하고 결과를 확인하기 위한 설정 가이드입니다.

## 1회 초기 설정 (경훈님이 직접 하셔야 하는 부분)

### GitHub Personal Access Token 발급
1. https://github.com/settings/tokens/new 접속
2. Token 이름: `cowork-ci`
3. 권한 선택:
   - `repo` (전체)
   - `workflow`
4. 생성 후 토큰 값을 복사

### GitHub Actions Secrets 등록 (선택 — Firebase 배포 원할 경우)
- Repository → Settings → Secrets and variables → Actions
- `GITHUB_TOKEN`은 자동 제공되므로 별도 등록 불필요

## Claude가 사용하는 API 명령어

```bash
# 빌드만 트리거 (Unit Test + APK 빌드)
curl -X POST \
  -H "Authorization: token {GITHUB_TOKEN}" \
  -H "Accept: application/vnd.github.v3+json" \
  https://api.github.com/repos/RyNusS/PhotoTransferApp/actions/workflows/android-ci.yml/dispatches \
  -d '{"ref":"master","inputs":{"run_emulator_tests":"false"}}'

# 에뮬레이터 테스트까지 트리거
curl -X POST \
  -H "Authorization: token {GITHUB_TOKEN}" \
  -H "Accept: application/vnd.github.v3+json" \
  https://api.github.com/repos/RyNusS/PhotoTransferApp/actions/workflows/android-ci.yml/dispatches \
  -d '{"ref":"master","inputs":{"run_emulator_tests":"true"}}'

# 최근 워크플로우 실행 결과 확인
curl -H "Authorization: token {GITHUB_TOKEN}" \
  https://api.github.com/repos/RyNusS/PhotoTransferApp/actions/runs?per_page=5
```

## 워크플로우 구성

| Job | 트리거 | 소요 시간 |
|-----|--------|----------|
| Build & Unit Test | push / workflow_dispatch | ~5분 |
| Emulator Test (API 29) | workflow_dispatch + input=true | ~15분 |
