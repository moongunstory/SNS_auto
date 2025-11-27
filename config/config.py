"""
설정 파일
바이낸스 API 키 및 분석 파라미터 설정
"""
import os
from dotenv import load_dotenv

load_dotenv()

# 바이낸스 API 설정 (선물 데이터는 API 키 없이도 공개 데이터 조회 가능)
BINANCE_API_KEY = os.getenv('BINANCE_API_KEY', '')
BINANCE_SECRET_KEY = os.getenv('BINANCE_SECRET_KEY', '')

# 급등 스캔 설정
SURGE_THRESHOLD = 10.0  # 급등 기준 (%)
SCAN_DAYS = 7  # 스캔할 기간 (일)
TIMEFRAME = '1h'  # 분석용 캔들 타임프레임

# 패턴 분석 설정
LOOKBACK_HOURS_BEFORE_SURGE = 72  # 급등 전 분석할 시간 (시간)
VOLUME_SURGE_MULTIPLIER = 2.0  # 거래량 급증 기준 (평균 대비 배수)

# 데이터베이스 설정
DB_PATH = 'data/surge_analysis.db'

# CoinGecko API 설정
COINGECKO_API_URL = 'https://api.coingecko.com/api/v3'

# 출력 설정
OUTPUT_DIR = 'data/reports'
CHART_DIR = 'data/charts'
