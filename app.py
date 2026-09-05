import streamlit as st
import requests
from datetime import datetime
from zoneinfo import ZoneInfo

st.set_page_config(page_title="ライフログ", layout="centered")

SUPABASE_URL = st.secrets["SUPABASE_URL"]
SUPABASE_KEY = st.secrets["SUPABASE_KEY"]


@st.cache_data(ttl=60)
def fetch_logs():
    url = f"{SUPABASE_URL}/rest/v1/logs?select=*&order=created_at.desc"
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
    }
    response = requests.get(url, headers=headers)
    response.raise_for_status()
    return response.json()


def format_jst(iso_string):
    dt = datetime.fromisoformat(iso_string.replace("Z", "+00:00"))
    dt_jst = dt.astimezone(ZoneInfo("Asia/Tokyo"))
    return dt_jst.strftime("%Y/%m/%d (%a) %H:%M")


st.title("ライフログ")

if st.button("更新"):
    st.cache_data.clear()

logs = fetch_logs()

if not logs:
    st.info("まだログがありません。")
else:
    for log in logs:
        st.markdown(f"**{format_jst(log['created_at'])}**")
        st.write(log["polished_text"])
        with st.expander("元のテキストを見る"):
            st.write(log["raw_text"])
        st.divider()
