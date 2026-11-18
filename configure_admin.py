from pathlib import Path
from getpass import getpass
import hashlib
import re
import secrets

root = Path(__file__).resolve().parent

username = input("관리자 아이디 [admin]: ").strip() or "admin"

if not re.fullmatch(r"[A-Za-z0-9._-]{1,80}", username):
    raise SystemExit("아이디는 영문·숫자·점·밑줄·하이픈으로 입력하세요.")

password = getpass("관리자 비밀번호 (8자 이상): ")

if len(password) < 8 or len(password) > 1024 or password.isspace():
    raise SystemExit("비밀번호는 공백만 사용하지 않고 8~1024자로 입력하세요.")

if password != getpass("비밀번호 확인: "):
    raise SystemExit("비밀번호가 일치하지 않습니다.")

path = root / "backend/admin-local.properties"

if path.exists():
    answer = input("기존 관리자 계정을 변경할까요? [y/N]: ")
    if answer.strip().lower() != "y":
        raise SystemExit("설정을 유지했습니다.")

salt = secrets.token_hex(16)

hashed = hashlib.pbkdf2_hmac(
    "sha256",
    password.encode(),
    salt.encode(),
    600000,
).hex()

path.write_text(
    f"pickme.admin-username={username}\n"
    f"pickme.admin-password-hash=pbkdf2:sha256:600000${salt}${hashed}\n",
    encoding="utf-8",
)

path.chmod(0o600)

print("관리자 계정 설정 완료")
print("backend 폴더에서 서버를 실행하거나 재시작하세요.")
