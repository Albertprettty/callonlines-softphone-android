#!/usr/bin/env python3
"""
Minimal SIP REGISTER + INVITE test (UDP).
Reads SIP_HOST, SIP_USER, SIP_PASS, TARGET, DURATION from env.
Reports response codes and exits 0 on success.

NO RTP/audio — only signaling. Enough to verify:
  - server is reachable
  - credentials are accepted (200 OK to REGISTER)
  - destination is routable (100 / 180 / 183 / 200 from INVITE)
"""
import os, socket, hashlib, re, time, sys, secrets, uuid

HOST     = os.environ["SIP_HOST"]
USER     = os.environ["SIP_USER"]
PASSWORD = os.environ["SIP_PASS"]
TARGET   = os.environ.get("TARGET",   "18777272932")
DURATION = int(os.environ.get("DURATION", "30"))
PORT     = int(os.environ.get("SIP_PORT", "5060"))

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(("0.0.0.0", 0))
LOCAL_IP, LOCAL_PORT = s.getsockname()
# Use external-facing IP for Contact/Via when possible
try:
    e = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    e.connect(("8.8.8.8", 80))
    LOCAL_IP = e.getsockname()[0]
    e.close()
except: pass
s.settimeout(8)

print(f"[+] Local {LOCAL_IP}:{LOCAL_PORT} -> Server {HOST}:{PORT}")
print(f"[+] User={USER}  Target=sip:{TARGET}@{HOST}")

def hh(s): return hashlib.md5(s.encode()).hexdigest()

def parse_status(msg):
    m = re.match(r"SIP/2\.0 (\d+) (.+?)\r\n", msg)
    return (int(m.group(1)), m.group(2)) if m else (0, "?")

def get_header(msg, name):
    m = re.search(rf"^{name}\s*:\s*(.+?)\r\n", msg, re.I | re.M)
    return m.group(1).strip() if m else None

def parse_auth(header):
    out = {}
    for k, v in re.findall(r'(\w+)\s*=\s*("([^"]*)"|([^,\s]+))', header):
        out[k.lower()] = v.strip('"')
    return out

def build_digest(auth, method, uri):
    realm  = auth["realm"]
    nonce  = auth["nonce"]
    qop    = auth.get("qop")
    opaque = auth.get("opaque")
    algo   = auth.get("algorithm", "MD5")
    ha1 = hh(f"{USER}:{realm}:{PASSWORD}")
    ha2 = hh(f"{method}:{uri}")
    cnonce, nc = secrets.token_hex(8), "00000001"
    if qop:
        response = hh(f"{ha1}:{nonce}:{nc}:{cnonce}:{qop}:{ha2}")
    else:
        response = hh(f"{ha1}:{nonce}:{ha2}")
    parts = [
        f'username="{USER}"', f'realm="{realm}"', f'nonce="{nonce}"',
        f'uri="{uri}"', f'algorithm={algo}', f'response="{response}"',
    ]
    if qop:
        parts += [f'qop={qop}', f'nc={nc}', f'cnonce="{cnonce}"']
    if opaque:
        parts += [f'opaque="{opaque}"']
    return "Digest " + ", ".join(parts)

def sendrecv(packet, expect_multi=False, timeout=8):
    s.settimeout(timeout)
    print("---> SENT:\n" + packet.decode(errors='replace').strip())
    s.sendto(packet, (HOST, PORT))
    replies = []
    end = time.time() + timeout
    while time.time() < end:
        try:
            data, _ = s.recvfrom(8192)
            text = data.decode(errors='replace')
            replies.append(text)
            code, phrase = parse_status(text)
            print(f"<--- RECV {code} {phrase}")
            print(text.strip())
            if not expect_multi or code >= 200:
                return text
        except socket.timeout:
            break
    return replies[-1] if replies else None

CALL_ID = uuid.uuid4().hex + "@" + LOCAL_IP
TAG = secrets.token_hex(6)
BRANCH = lambda: "z9hG4bK." + secrets.token_hex(8)

def register(cseq, auth_header=None):
    branch = BRANCH()
    via    = f"SIP/2.0/UDP {LOCAL_IP}:{LOCAL_PORT};rport;branch={branch}"
    fr     = f"<sip:{USER}@{HOST}>;tag={TAG}"
    to     = f"<sip:{USER}@{HOST}>"
    uri    = f"sip:{HOST}"
    pkt = [
        f"REGISTER {uri} SIP/2.0",
        f"Via: {via}",
        "Max-Forwards: 70",
        f"From: {fr}",
        f"To: {to}",
        f"Call-ID: {CALL_ID}",
        f"CSeq: {cseq} REGISTER",
        f"Contact: <sip:{USER}@{LOCAL_IP}:{LOCAL_PORT};transport=udp>",
        "Expires: 600",
        "Allow: INVITE, ACK, CANCEL, BYE, OPTIONS",
        "User-Agent: CallOnLines-Test/1.0",
    ]
    if auth_header:
        pkt.append(f"Authorization: {auth_header}")
    pkt += ["Content-Length: 0", "", ""]
    return sendrecv("\r\n".join(pkt).encode())

print("\n=== STEP 1: REGISTER ===")
resp = register(1)
if not resp:
    print("FAIL: no response to REGISTER")
    sys.exit(1)
code, _ = parse_status(resp)
if code == 401 or code == 407:
    auth_hdr = get_header(resp, "WWW-Authenticate") or get_header(resp, "Proxy-Authenticate")
    if not auth_hdr:
        print("FAIL: 401/407 but no auth header"); sys.exit(1)
    auth = parse_auth(auth_hdr)
    method_uri = f"sip:{HOST}"
    print(f"[+] Got challenge realm={auth.get('realm')} nonce={auth.get('nonce')[:16]}...")
    digest = build_digest(auth, "REGISTER", method_uri)
    resp = register(2, digest)
    if not resp:
        print("FAIL: no response to authed REGISTER"); sys.exit(1)
    code, phrase = parse_status(resp)

if code == 200:
    print(f"\n[OK] REGISTER 200 — credentials accepted")
else:
    print(f"\n[FAIL] REGISTER got {code} {phrase}")
    sys.exit(2)

# --- INVITE ---
print("\n=== STEP 2: INVITE ===")
CALL_ID2 = uuid.uuid4().hex + "@" + LOCAL_IP

def make_sdp():
    return (
        "v=0\r\n"
        f"o=- {int(time.time())} {int(time.time())} IN IP4 {LOCAL_IP}\r\n"
        "s=CallOnLines-Test\r\n"
        f"c=IN IP4 {LOCAL_IP}\r\n"
        "t=0 0\r\n"
        f"m=audio {LOCAL_PORT+2} RTP/AVP 0 8\r\n"
        "a=rtpmap:0 PCMU/8000\r\n"
        "a=rtpmap:8 PCMA/8000\r\n"
        "a=sendrecv\r\n"
    )

def invite(cseq, target, auth_header=None, target_via=None, target_branch=None):
    sdp = make_sdp()
    branch = target_branch or BRANCH()
    via    = f"SIP/2.0/UDP {LOCAL_IP}:{LOCAL_PORT};rport;branch={branch}"
    fr     = f"<sip:{USER}@{HOST}>;tag={TAG}"
    to     = f"<sip:{target}@{HOST}>"
    uri    = f"sip:{target}@{HOST}"
    pkt = [
        f"INVITE {uri} SIP/2.0",
        f"Via: {via}",
        "Max-Forwards: 70",
        f"From: {fr}",
        f"To: {to}",
        f"Call-ID: {CALL_ID2}",
        f"CSeq: {cseq} INVITE",
        f"Contact: <sip:{USER}@{LOCAL_IP}:{LOCAL_PORT};transport=udp>",
        "Allow: INVITE, ACK, CANCEL, BYE, OPTIONS",
        "User-Agent: CallOnLines-Test/1.0",
        "Content-Type: application/sdp",
    ]
    if auth_header:
        pkt.append(f"Authorization: {auth_header}")
    pkt += [
        f"Content-Length: {len(sdp)}",
        "",
        sdp,
    ]
    return sendrecv("\r\n".join(pkt).encode(), expect_multi=True, timeout=15)

# First INVITE
resp = invite(1, TARGET)
if not resp:
    print("FAIL: no response to INVITE"); sys.exit(1)
code, phrase = parse_status(resp)

if code in (401, 407):
    auth_hdr = get_header(resp, "WWW-Authenticate") or get_header(resp, "Proxy-Authenticate")
    if auth_hdr:
        auth = parse_auth(auth_hdr)
        method_uri = f"sip:{TARGET}@{HOST}"
        digest = build_digest(auth, "INVITE", method_uri)
        # send ACK to the auth challenge
        # not strictly needed for proxy auth, but for some servers required
        resp = invite(2, TARGET, digest)
        code, phrase = parse_status(resp)

print(f"\n[{'OK' if 200 <= code < 300 else 'INFO'}] First definitive INVITE response: {code} {phrase}")

if code == 200:
    print("\n=== STEP 3: ACK + wait + BYE ===")
    # Extract To with tag for ACK/BYE
    to_hdr = get_header(resp, "To") or f"<sip:{TARGET}@{HOST}>"
    branch = BRANCH()
    via = f"SIP/2.0/UDP {LOCAL_IP}:{LOCAL_PORT};rport;branch={branch}"
    ack = "\r\n".join([
        f"ACK sip:{TARGET}@{HOST} SIP/2.0",
        f"Via: {via}",
        "Max-Forwards: 70",
        f"From: <sip:{USER}@{HOST}>;tag={TAG}",
        f"To: {to_hdr}",
        f"Call-ID: {CALL_ID2}",
        "CSeq: 2 ACK",
        f"Contact: <sip:{USER}@{LOCAL_IP}:{LOCAL_PORT}>",
        "Content-Length: 0",
        "", "",
    ]).encode()
    print("---> ACK")
    s.sendto(ack, (HOST, PORT))
    print(f"[+] Waiting {DURATION}s while call is active...")
    time.sleep(DURATION)
    print("[+] Sending BYE")
    bye = "\r\n".join([
        f"BYE sip:{TARGET}@{HOST} SIP/2.0",
        f"Via: SIP/2.0/UDP {LOCAL_IP}:{LOCAL_PORT};rport;branch={BRANCH()}",
        "Max-Forwards: 70",
        f"From: <sip:{USER}@{HOST}>;tag={TAG}",
        f"To: {to_hdr}",
        f"Call-ID: {CALL_ID2}",
        "CSeq: 3 BYE",
        "Content-Length: 0",
        "", "",
    ]).encode()
    s.sendto(bye, (HOST, PORT))
    try:
        data, _ = s.recvfrom(4096); print(data.decode(errors='replace'))
    except: pass
    print("\n[OK] Call completed.")
    sys.exit(0)
elif 100 <= code < 200:
    print(f"[INFO] Got provisional {code} only — destination is probably ringing or routing")
    sys.exit(3)
elif code in (401, 407):
    print(f"[FAIL] Authentication rejected on INVITE ({code})")
    sys.exit(4)
else:
    print(f"[FAIL] Server returned {code} {phrase}")
    sys.exit(5)
