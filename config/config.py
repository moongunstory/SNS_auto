"""
설정 파일
바이낸스 API 키 및 분석 파라미터 설정
"""
import os
from dotenv import load_dotenv

load_dotenv()

# ==================== API 설정 ====================
# 바이낸스 API 설정 (선물 데이터는 API 키 없이도 공개 데이터 조회 가능)
BINANCE_API_KEY = os.getenv('BINANCE_API_KEY', '')
BINANCE_SECRET_KEY = os.getenv('BINANCE_SECRET_KEY', '')

# LunarCrush API 설정 (소셜 데이터)
LUNARCRUSH_API_KEY = os.getenv('LUNARCRUSH_API_KEY', '')

# CoinGecko API 설정 (무료, API 키 불필요)
COINGECKO_API_URL = 'https://api.coingecko.com/api/v3'

# ==================== 급등 스캔 설정 ====================
SURGE_THRESHOLD = 10.0  # 급등 기준 (%)
SCAN_DAYS = 180  # 스캔할 기간 (일) - 3-6개월로 확대
TIMEFRAME = '1h'  # 분석용 캔들 타임프레임

# ==================== 패턴 분석 설정 ====================
LOOKBACK_HOURS_BEFORE_SURGE = 72  # 급등 전 분석할 시간 (시간)
VOLUME_SURGE_MULTIPLIER = 2.0  # 거래량 급증 기준 (평균 대비 배수)

# 패턴 감지 임계값
PATTERN_SCORE_THRESHOLD = 70.0  # 패턴 점수 임계값 (0-100)
MIN_SURGE_SAMPLES = 50  # 패턴 학습에 필요한 최소 급등 샘플 수

# ==================== 머신러닝 설정 ====================
# 데이터 분할 비율
TRAIN_TEST_SPLIT_RATIO = 0.8  # 80% 학습, 20% 테스트

# Feature 중요도 임계값
FEATURE_IMPORTANCE_THRESHOLD = 0.01

# 모델 저장 경로
MODEL_DIR = 'data/models'
MODEL_FILE = 'surge_prediction_model.pkl'

# ==================== 백테스팅 설정 ====================
BACKTEST_INITIAL_BALANCE = 10000  # 초기 자본 (USDT)
BACKTEST_POSITION_SIZE = 0.1  # 포지션 크기 (자본의 10%)
BACKTEST_STOP_LOSS = 0.05  # 손절 비율 (5%)
BACKTEST_TAKE_PROFIT = 0.15  # 익절 비율 (15%)

# ==================== 실시간 예측 설정 ====================
PREDICTION_SCAN_INTERVAL = 300  # 스캔 간격 (초, 5분)
PREDICTION_MIN_PROBABILITY = 0.7  # 최소 예측 확률 (70%)
MAX_CONCURRENT_ALERTS = 5  # 동시 알림 최대 개수

# ==================== 데이터베이스 설정 ====================
DB_PATH = 'data/surge_analysis.db'
DATA_RETENTION_DAYS = 365  # 데이터 보관 기간 (일)

# ==================== 출력 설정 ====================
OUTPUT_DIR = 'data/reports'
CHART_DIR = 'data/charts'
LOG_DIR = 'data/logs'

# ==================== 기타 설정 ====================
# 로깅 레벨
LOG_LEVEL = 'INFO'  # DEBUG, INFO, WARNING, ERROR

# 캐시 설정
ENABLE_CACHE = True
CACHE_EXPIRY_MINUTES = 60
