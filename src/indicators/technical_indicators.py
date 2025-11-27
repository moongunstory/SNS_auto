"""
기술적 지표 계산 모듈
RSI, MACD, 볼린저밴드, 이동평균 등 다양한 지표 계산
"""
import pandas as pd
import numpy as np
from typing import Dict, Optional
import warnings
warnings.filterwarnings('ignore')


class TechnicalIndicators:
    """기술적 지표 계산 클래스"""

    @staticmethod
    def calculate_rsi(df: pd.DataFrame, period: int = 14, column: str = 'close') -> pd.Series:
        """
        RSI (Relative Strength Index) 계산

        Args:
            df: OHLCV DataFrame
            period: RSI 기간 (기본값 14)
            column: 계산에 사용할 컬럼 (기본값 'close')

        Returns:
            pd.Series: RSI 값 (0-100)
        """
        delta = df[column].diff()
        gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()

        rs = gain / loss
        rsi = 100 - (100 / (1 + rs))

        return rsi

    @staticmethod
    def calculate_macd(df: pd.DataFrame, fast: int = 12, slow: int = 26,
                      signal: int = 9, column: str = 'close') -> Dict[str, pd.Series]:
        """
        MACD (Moving Average Convergence Divergence) 계산

        Args:
            df: OHLCV DataFrame
            fast: 빠른 이동평균 기간 (기본값 12)
            slow: 느린 이동평균 기간 (기본값 26)
            signal: 시그널 라인 기간 (기본값 9)
            column: 계산에 사용할 컬럼

        Returns:
            Dict: macd, signal, histogram 포함
        """
        ema_fast = df[column].ewm(span=fast, adjust=False).mean()
        ema_slow = df[column].ewm(span=slow, adjust=False).mean()

        macd_line = ema_fast - ema_slow
        signal_line = macd_line.ewm(span=signal, adjust=False).mean()
        histogram = macd_line - signal_line

        return {
            'macd': macd_line,
            'signal': signal_line,
            'histogram': histogram
        }

    @staticmethod
    def calculate_bollinger_bands(df: pd.DataFrame, period: int = 20,
                                  std_dev: float = 2.0, column: str = 'close') -> Dict[str, pd.Series]:
        """
        볼린저 밴드 계산

        Args:
            df: OHLCV DataFrame
            period: 이동평균 기간 (기본값 20)
            std_dev: 표준편차 배수 (기본값 2.0)
            column: 계산에 사용할 컬럼

        Returns:
            Dict: upper, middle, lower 밴드
        """
        middle_band = df[column].rolling(window=period).mean()
        std = df[column].rolling(window=period).std()

        upper_band = middle_band + (std * std_dev)
        lower_band = middle_band - (std * std_dev)

        return {
            'upper': upper_band,
            'middle': middle_band,
            'lower': lower_band
        }

    @staticmethod
    def calculate_ema(df: pd.DataFrame, period: int, column: str = 'close') -> pd.Series:
        """
        EMA (Exponential Moving Average) 계산

        Args:
            df: OHLCV DataFrame
            period: EMA 기간
            column: 계산에 사용할 컬럼

        Returns:
            pd.Series: EMA 값
        """
        return df[column].ewm(span=period, adjust=False).mean()

    @staticmethod
    def calculate_sma(df: pd.DataFrame, period: int, column: str = 'close') -> pd.Series:
        """
        SMA (Simple Moving Average) 계산

        Args:
            df: OHLCV DataFrame
            period: SMA 기간
            column: 계산에 사용할 컬럼

        Returns:
            pd.Series: SMA 값
        """
        return df[column].rolling(window=period).mean()

    @staticmethod
    def calculate_atr(df: pd.DataFrame, period: int = 14) -> pd.Series:
        """
        ATR (Average True Range) 계산 - 변동성 지표

        Args:
            df: OHLCV DataFrame
            period: ATR 기간

        Returns:
            pd.Series: ATR 값
        """
        high_low = df['high'] - df['low']
        high_close = np.abs(df['high'] - df['close'].shift())
        low_close = np.abs(df['low'] - df['close'].shift())

        tr = pd.concat([high_low, high_close, low_close], axis=1).max(axis=1)
        atr = tr.rolling(window=period).mean()

        return atr

    @staticmethod
    def calculate_stochastic(df: pd.DataFrame, k_period: int = 14,
                            d_period: int = 3) -> Dict[str, pd.Series]:
        """
        스토캐스틱 오실레이터 계산

        Args:
            df: OHLCV DataFrame
            k_period: %K 기간 (기본값 14)
            d_period: %D 기간 (기본값 3)

        Returns:
            Dict: %K, %D 값
        """
        lowest_low = df['low'].rolling(window=k_period).min()
        highest_high = df['high'].rolling(window=k_period).max()

        k_percent = 100 * ((df['close'] - lowest_low) / (highest_high - lowest_low))
        d_percent = k_percent.rolling(window=d_period).mean()

        return {
            'k': k_percent,
            'd': d_percent
        }

    @staticmethod
    def calculate_obv(df: pd.DataFrame) -> pd.Series:
        """
        OBV (On-Balance Volume) 계산 - 거래량 추세 지표

        Args:
            df: OHLCV DataFrame

        Returns:
            pd.Series: OBV 값
        """
        obv = (np.sign(df['close'].diff()) * df['volume']).fillna(0).cumsum()
        return obv

    @staticmethod
    def calculate_vwap(df: pd.DataFrame) -> pd.Series:
        """
        VWAP (Volume Weighted Average Price) 계산

        Args:
            df: OHLCV DataFrame

        Returns:
            pd.Series: VWAP 값
        """
        typical_price = (df['high'] + df['low'] + df['close']) / 3
        vwap = (typical_price * df['volume']).cumsum() / df['volume'].cumsum()
        return vwap

    @staticmethod
    def calculate_mfi(df: pd.DataFrame, period: int = 14) -> pd.Series:
        """
        MFI (Money Flow Index) 계산 - RSI의 거래량 버전

        Args:
            df: OHLCV DataFrame
            period: MFI 기간

        Returns:
            pd.Series: MFI 값 (0-100)
        """
        typical_price = (df['high'] + df['low'] + df['close']) / 3
        money_flow = typical_price * df['volume']

        positive_flow = money_flow.where(typical_price > typical_price.shift(1), 0).rolling(period).sum()
        negative_flow = money_flow.where(typical_price < typical_price.shift(1), 0).rolling(period).sum()

        mfi = 100 - (100 / (1 + positive_flow / negative_flow))
        return mfi

    @staticmethod
    def calculate_adx(df: pd.DataFrame, period: int = 14) -> pd.Series:
        """
        ADX (Average Directional Index) 계산 - 추세 강도 지표

        Args:
            df: OHLCV DataFrame
            period: ADX 기간

        Returns:
            pd.Series: ADX 값
        """
        high_diff = df['high'].diff()
        low_diff = -df['low'].diff()

        plus_dm = high_diff.where((high_diff > low_diff) & (high_diff > 0), 0)
        minus_dm = low_diff.where((low_diff > high_diff) & (low_diff > 0), 0)

        atr = TechnicalIndicators.calculate_atr(df, period)

        plus_di = 100 * (plus_dm.rolling(period).mean() / atr)
        minus_di = 100 * (minus_dm.rolling(period).mean() / atr)

        dx = 100 * np.abs(plus_di - minus_di) / (plus_di + minus_di)
        adx = dx.rolling(period).mean()

        return adx

    @staticmethod
    def calculate_all_indicators(df: pd.DataFrame) -> pd.DataFrame:
        """
        모든 기술적 지표를 한 번에 계산

        Args:
            df: OHLCV DataFrame

        Returns:
            DataFrame: 모든 지표가 추가된 DataFrame
        """
        result = df.copy()

        # RSI
        result['rsi_14'] = TechnicalIndicators.calculate_rsi(df, 14)
        result['rsi_7'] = TechnicalIndicators.calculate_rsi(df, 7)

        # MACD
        macd = TechnicalIndicators.calculate_macd(df)
        result['macd'] = macd['macd']
        result['macd_signal'] = macd['signal']
        result['macd_histogram'] = macd['histogram']

        # 볼린저 밴드
        bb = TechnicalIndicators.calculate_bollinger_bands(df)
        result['bb_upper'] = bb['upper']
        result['bb_middle'] = bb['middle']
        result['bb_lower'] = bb['lower']
        result['bb_width'] = (bb['upper'] - bb['lower']) / bb['middle']  # 밴드 폭 정규화

        # 이동평균
        result['ema_9'] = TechnicalIndicators.calculate_ema(df, 9)
        result['ema_21'] = TechnicalIndicators.calculate_ema(df, 21)
        result['ema_50'] = TechnicalIndicators.calculate_ema(df, 50)
        result['sma_20'] = TechnicalIndicators.calculate_sma(df, 20)
        result['sma_50'] = TechnicalIndicators.calculate_sma(df, 50)

        # ATR
        result['atr_14'] = TechnicalIndicators.calculate_atr(df, 14)

        # 스토캐스틱
        stoch = TechnicalIndicators.calculate_stochastic(df)
        result['stoch_k'] = stoch['k']
        result['stoch_d'] = stoch['d']

        # OBV
        result['obv'] = TechnicalIndicators.calculate_obv(df)

        # VWAP
        result['vwap'] = TechnicalIndicators.calculate_vwap(df)

        # MFI
        result['mfi_14'] = TechnicalIndicators.calculate_mfi(df, 14)

        # ADX
        result['adx_14'] = TechnicalIndicators.calculate_adx(df, 14)

        # 추가 파생 지표
        result['volume_sma_20'] = result['volume'].rolling(20).mean()
        result['volume_ratio'] = result['volume'] / result['volume_sma_20']  # 거래량 배율

        # 가격 위치 (볼린저 밴드 내 위치)
        result['price_position'] = (result['close'] - result['bb_lower']) / (result['bb_upper'] - result['bb_lower'])

        return result

    @staticmethod
    def detect_patterns(df: pd.DataFrame) -> Dict:
        """
        기술적 패턴 감지

        Args:
            df: 지표가 계산된 DataFrame

        Returns:
            Dict: 감지된 패턴들
        """
        patterns = {}

        if 'rsi_14' in df.columns:
            latest_rsi = df['rsi_14'].iloc[-1]
            patterns['rsi_oversold'] = latest_rsi < 30
            patterns['rsi_overbought'] = latest_rsi > 70

        if 'macd_histogram' in df.columns:
            # MACD 골든 크로스/데드 크로스
            patterns['macd_golden_cross'] = (
                df['macd_histogram'].iloc[-1] > 0 and
                df['macd_histogram'].iloc[-2] <= 0
            )
            patterns['macd_dead_cross'] = (
                df['macd_histogram'].iloc[-1] < 0 and
                df['macd_histogram'].iloc[-2] >= 0
            )

        if 'bb_upper' in df.columns and 'bb_lower' in df.columns:
            # 볼린저 밴드 돌파
            patterns['bb_breakout_upper'] = df['close'].iloc[-1] > df['bb_upper'].iloc[-1]
            patterns['bb_breakout_lower'] = df['close'].iloc[-1] < df['bb_lower'].iloc[-1]
            patterns['bb_squeeze'] = df['bb_width'].iloc[-1] < df['bb_width'].rolling(20).mean().iloc[-1] * 0.5

        if 'ema_9' in df.columns and 'ema_21' in df.columns:
            # EMA 크로스오버
            patterns['ema_golden_cross'] = (
                df['ema_9'].iloc[-1] > df['ema_21'].iloc[-1] and
                df['ema_9'].iloc[-2] <= df['ema_21'].iloc[-2]
            )
            patterns['ema_dead_cross'] = (
                df['ema_9'].iloc[-1] < df['ema_21'].iloc[-1] and
                df['ema_9'].iloc[-2] >= df['ema_21'].iloc[-2]
            )

        if 'volume_ratio' in df.columns:
            # 거래량 급증
            patterns['volume_spike'] = df['volume_ratio'].iloc[-1] > 2.0
            patterns['extreme_volume'] = df['volume_ratio'].iloc[-1] > 3.0

        return patterns
