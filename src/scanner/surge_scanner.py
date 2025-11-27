"""
급등 코인 스캐너 모듈
최근 N일 이내 급등한 코인을 탐지
"""
import pandas as pd
from datetime import datetime, timedelta
from typing import List, Dict
from src.collector.binance_collector import BinanceCollector


class SurgeScanner:
    """급등 코인 탐지 스캐너"""

    def __init__(self, collector: BinanceCollector, surge_threshold: float = 10.0):
        """
        Args:
            collector: BinanceCollector 인스턴스
            surge_threshold: 급등 기준 (%, 기본값 10%)
        """
        self.collector = collector
        self.surge_threshold = surge_threshold

    def scan_current_surges(self) -> pd.DataFrame:
        """
        현재 24시간 기준 급등 코인 스캔

        Returns:
            DataFrame: 급등 코인 리스트 (변동률 기준 내림차순 정렬)
        """
        print(f"Scanning for coins with {self.surge_threshold}%+ surge...")

        # 모든 코인의 24시간 변동률 조회
        df = self.collector.get_24h_price_change()

        if df.empty:
            print("No data available")
            return pd.DataFrame()

        # 급등 기준 필터링
        surged = df[df['change_24h'] >= self.surge_threshold].copy()

        print(f"Found {len(surged)} coins with {self.surge_threshold}%+ surge")

        return surged

    def scan_historical_surges(self, days: int = 7) -> List[Dict]:
        """
        과거 N일간 급등 이벤트 탐지
        각 코인의 OHLCV 데이터를 분석하여 급등 시점을 찾음

        Args:
            days: 조회할 일수

        Returns:
            List[Dict]: 급등 이벤트 정보 리스트
        """
        print(f"Scanning historical surges for the last {days} days...")

        # 모든 USDT 선물 심볼 조회
        symbols = self.collector.get_all_usdt_futures()
        print(f"Total {len(symbols)} symbols to scan")

        surge_events = []

        # 각 심볼에 대해 과거 데이터 분석
        for i, symbol in enumerate(symbols, 1):
            if i % 50 == 0:
                print(f"Progress: {i}/{len(symbols)}")

            try:
                # 과거 1시간 봉 데이터 조회
                df = self.collector.get_historical_ohlcv(symbol, timeframe='1h', days=days)

                if df.empty or len(df) < 24:
                    continue

                # 24시간 변동률 계산 (각 시점 기준)
                df['change_24h'] = ((df['close'] - df['close'].shift(24)) / df['close'].shift(24)) * 100

                # 급등 이벤트 탐지
                surge_rows = df[df['change_24h'] >= self.surge_threshold]

                for _, row in surge_rows.iterrows():
                    surge_events.append({
                        'symbol': symbol,
                        'surge_timestamp': row['timestamp'],
                        'surge_change': row['change_24h'],
                        'price_before': row['close'] / (1 + row['change_24h'] / 100),
                        'price_after': row['close'],
                        'volume': row['volume'],
                    })

            except Exception as e:
                print(f"Error processing {symbol}: {e}")
                continue

        print(f"\nFound {len(surge_events)} surge events in the last {days} days")

        # DataFrame으로 변환 및 정렬
        if surge_events:
            df_events = pd.DataFrame(surge_events)
            df_events = df_events.sort_values('surge_change', ascending=False)
            return df_events.to_dict('records')

        return []

    def get_surge_details(self, symbol: str, surge_timestamp: datetime,
                         lookback_hours: int = 72) -> Dict:
        """
        급등 이벤트의 상세 정보 수집
        급등 전후의 데이터를 수집하여 분석에 활용

        Args:
            symbol: 거래 심볼
            surge_timestamp: 급등 발생 시점
            lookback_hours: 급등 전 수집할 시간 (시간 단위)

        Returns:
            Dict: 급등 전후 상세 데이터
        """
        # 급등 전후 데이터 수집 기간 계산
        total_days = (lookback_hours // 24) + 3  # 여유있게 계산

        # OHLCV 데이터 수집
        ohlcv = self.collector.get_historical_ohlcv(
            symbol,
            timeframe='1h',
            days=total_days
        )

        if ohlcv.empty:
            return {}

        # 급등 시점 기준으로 데이터 분할
        before_surge = ohlcv[ohlcv['timestamp'] < surge_timestamp].tail(lookback_hours)
        after_surge = ohlcv[ohlcv['timestamp'] >= surge_timestamp].head(24)

        # 펀딩비율 히스토리
        funding_history = self.collector.get_funding_rate_history(symbol, days=total_days)

        # 현재 데이터
        current_funding = self.collector.get_funding_rate(symbol)
        current_oi = self.collector.get_open_interest(symbol)
        long_short_ratio = self.collector.get_long_short_ratio(symbol)

        return {
            'symbol': symbol,
            'surge_timestamp': surge_timestamp,
            'ohlcv_before': before_surge,
            'ohlcv_after': after_surge,
            'funding_history': funding_history,
            'current_funding': current_funding,
            'current_oi': current_oi,
            'long_short_ratio': long_short_ratio,
        }

    def filter_by_criteria(self, surge_df: pd.DataFrame,
                          min_volume: float = 0,
                          max_market_cap: float = float('inf')) -> pd.DataFrame:
        """
        추가 조건으로 급등 코인 필터링

        Args:
            surge_df: 급등 코인 DataFrame
            min_volume: 최소 거래량
            max_market_cap: 최대 시가총액 (저시총 필터링용)

        Returns:
            DataFrame: 필터링된 급등 코인
        """
        filtered = surge_df.copy()

        if min_volume > 0:
            filtered = filtered[filtered['volume_24h'] >= min_volume]

        # 시가총액 필터링은 별도 데이터 필요 (추후 확장)

        return filtered
