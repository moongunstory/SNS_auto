"""
Feature 엔지니어링 모듈
OHLCV 데이터로부터 급등 예측을 위한 Feature 추출
"""
import pandas as pd
import numpy as np
from typing import Dict, List
from src.indicators.technical_indicators import TechnicalIndicators


class FeatureEngineer:
    """급등 예측을 위한 Feature 생성"""

    def __init__(self):
        self.ti = TechnicalIndicators()

    def create_features(self, ohlcv: pd.DataFrame) -> pd.DataFrame:
        """
        OHLCV 데이터로부터 Feature 생성

        Args:
            ohlcv: OHLCV DataFrame

        Returns:
            DataFrame: Feature가 추가된 DataFrame
        """
        if ohlcv.empty or len(ohlcv) < 100:
            return pd.DataFrame()

        # 기술적 지표 계산
        df = self.ti.calculate_all_indicators(ohlcv.copy())

        # 추가 Feature 생성
        df = self._add_price_features(df)
        df = self._add_volume_features(df)
        df = self._add_momentum_features(df)
        df = self._add_volatility_features(df)
        df = self._add_pattern_features(df)

        return df

    def _add_price_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """가격 관련 Feature 추가"""
        # 가격 변화율 (여러 기간)
        for period in [1, 3, 6, 12, 24]:
            df[f'price_change_{period}h'] = df['close'].pct_change(period) * 100

        # 고가/저가 대비 종가 위치
        df['hl_position'] = (df['close'] - df['low']) / (df['high'] - df['low'])
        df['hl_position'] = df['hl_position'].fillna(0.5)

        # 캔들 크기
        df['candle_size'] = (df['close'] - df['open']) / df['open'] * 100

        # 상/하 그림자 비율
        df['upper_shadow'] = (df['high'] - df[['open', 'close']].max(axis=1)) / df['close']
        df['lower_shadow'] = (df[['open', 'close']].min(axis=1) - df['low']) / df['close']

        return df

    def _add_volume_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """거래량 관련 Feature 추가"""
        # 거래량 변화율
        for period in [1, 3, 6, 12, 24]:
            df[f'volume_change_{period}h'] = df['volume'].pct_change(period) * 100

        # 가격-거래량 상관관계
        df['price_volume_corr_12h'] = df['close'].rolling(12).corr(df['volume'])
        df['price_volume_corr_24h'] = df['close'].rolling(24).corr(df['volume'])

        # 거래량 추세
        df['volume_trend_24h'] = (df['volume'].rolling(24).mean() /
                                  df['volume'].rolling(48).mean())

        return df

    def _add_momentum_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """모멘텀 관련 Feature 추가"""
        # ROC (Rate of Change)
        for period in [6, 12, 24]:
            df[f'roc_{period}h'] = ((df['close'] - df['close'].shift(period)) /
                                   df['close'].shift(period)) * 100

        # 이동평균 간 거리
        if 'ema_9' in df.columns and 'ema_21' in df.columns:
            df['ema_9_21_dist'] = (df['ema_9'] - df['ema_21']) / df['ema_21'] * 100

        if 'ema_21' in df.columns and 'ema_50' in df.columns:
            df['ema_21_50_dist'] = (df['ema_21'] - df['ema_50']) / df['ema_50'] * 100

        # MACD 히스토그램 변화율
        if 'macd_histogram' in df.columns:
            df['macd_hist_change'] = df['macd_histogram'].diff()

        return df

    def _add_volatility_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """변동성 관련 Feature 추가"""
        # 실현 변동성 (여러 기간)
        for period in [6, 12, 24, 48]:
            returns = df['close'].pct_change()
            df[f'volatility_{period}h'] = returns.rolling(period).std() * 100

        # 변동성 비율 (단기/장기)
        if 'volatility_12h' in df.columns and 'volatility_48h' in df.columns:
            df['volatility_ratio'] = df['volatility_12h'] / df['volatility_48h']

        # High-Low 범위
        df['hl_range_24h'] = (df['high'].rolling(24).max() - df['low'].rolling(24).min()) / df['close']

        return df

    def _add_pattern_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """패턴 관련 Feature 추가"""
        # 연속 상승/하락 캔들 카운트
        df['is_green'] = (df['close'] > df['open']).astype(int)
        df['consecutive_greens'] = df['is_green'].groupby(
            (df['is_green'] != df['is_green'].shift()).cumsum()
        ).cumsum()

        df['is_red'] = (df['close'] < df['open']).astype(int)
        df['consecutive_reds'] = df['is_red'].groupby(
            (df['is_red'] != df['is_red'].shift()).cumsum()
        ).cumsum()

        # 최근 N시간 내 신고가/신저가 여부
        df['new_high_24h'] = (df['high'] >= df['high'].rolling(24).max()).astype(int)
        df['new_low_24h'] = (df['low'] <= df['low'].rolling(24).min()).astype(int)

        # 가격 가속도
        df['price_acceleration'] = df['close'].diff().diff()

        return df

    def select_features(self, df: pd.DataFrame) -> List[str]:
        """
        모델 학습에 사용할 Feature 선택

        Args:
            df: Feature가 추가된 DataFrame

        Returns:
            List[str]: 선택된 Feature 이름 리스트
        """
        # NaN이 너무 많은 컬럼 제외
        valid_cols = []
        for col in df.columns:
            if col not in ['timestamp', 'symbol', 'open', 'high', 'low', 'close', 'volume']:
                nan_ratio = df[col].isna().sum() / len(df)
                if nan_ratio < 0.3:  # 30% 미만 결측치
                    valid_cols.append(col)

        return valid_cols

    def create_target_variable(self, df: pd.DataFrame, surge_threshold: float = 10.0,
                              lookahead_hours: int = 24) -> pd.Series:
        """
        타겟 변수 생성 (향후 N시간 내 급등 여부)

        Args:
            df: OHLCV DataFrame
            surge_threshold: 급등 기준 (%)
            lookahead_hours: 예측 기간 (시간)

        Returns:
            pd.Series: 타겟 변수 (1: 급등, 0: 비급등)
        """
        # 향후 lookahead_hours 시간 내 최대 상승률 계산
        max_future_price = df['close'].shift(-lookahead_hours).rolling(
            window=lookahead_hours, min_periods=1
        ).max()

        future_return = ((max_future_price - df['close']) / df['close']) * 100

        # 급등 여부 (1 or 0)
        target = (future_return >= surge_threshold).astype(int)

        return target

    def prepare_training_data(self, surge_events: List[Dict], non_surge_samples: List[Dict],
                             ohlcv_data: Dict[str, pd.DataFrame]) -> pd.DataFrame:
        """
        학습 데이터 준비 (급등 이벤트 + 비급등 샘플)

        Args:
            surge_events: 급등 이벤트 리스트
            non_surge_samples: 비급등 샘플 리스트
            ohlcv_data: 심볼별 OHLCV 데이터

        Returns:
            DataFrame: 학습용 데이터 (Features + Target)
        """
        all_samples = []

        # 급등 이벤트 처리
        for event in surge_events:
            symbol = event['symbol']
            surge_time = event['surge_timestamp']

            if symbol in ohlcv_data:
                ohlcv = ohlcv_data[symbol]
                # 급등 직전 데이터 추출
                before_surge = ohlcv[ohlcv['timestamp'] < surge_time].tail(100)

                if len(before_surge) >= 50:
                    features_df = self.create_features(before_surge)
                    if not features_df.empty:
                        # 급등 직전 시점의 Feature 추출
                        last_row = features_df.iloc[-1].copy()
                        last_row['target'] = 1  # 급등
                        last_row['symbol'] = symbol
                        all_samples.append(last_row)

        # 비급등 샘플 처리 (클래스 균형 맞추기)
        for sample in non_surge_samples[:len(surge_events)]:
            symbol = sample['symbol']
            timestamp = sample['timestamp']

            if symbol in ohlcv_data:
                ohlcv = ohlcv_data[symbol]
                before_time = ohlcv[ohlcv['timestamp'] < timestamp].tail(100)

                if len(before_time) >= 50:
                    features_df = self.create_features(before_time)
                    if not features_df.empty:
                        last_row = features_df.iloc[-1].copy()
                        last_row['target'] = 0  # 비급등
                        last_row['symbol'] = symbol
                        all_samples.append(last_row)

        return pd.DataFrame(all_samples)
