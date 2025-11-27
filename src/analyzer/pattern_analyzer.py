"""
패턴 분석 모듈
급등한 코인들의 공통 패턴 탐색 및 점수화
"""
import pandas as pd
import numpy as np
from typing import Dict, List, Tuple
from datetime import datetime


class PatternAnalyzer:
    """급등 전 패턴 분석 및 공통점 탐색"""

    def __init__(self, volume_surge_multiplier: float = 2.0):
        """
        Args:
            volume_surge_multiplier: 거래량 급증 기준 배수
        """
        self.volume_surge_multiplier = volume_surge_multiplier

    def analyze_volume_pattern(self, ohlcv_before: pd.DataFrame) -> Dict:
        """
        급등 전 거래량 패턴 분석

        Args:
            ohlcv_before: 급등 전 OHLCV 데이터

        Returns:
            Dict: 거래량 패턴 분석 결과
        """
        if ohlcv_before.empty or len(ohlcv_before) < 24:
            return {}

        # 평균 거래량 계산
        avg_volume = ohlcv_before['volume'].mean()
        recent_volumes = ohlcv_before.tail(12)['volume']  # 최근 12시간

        # 거래량 급증 여부
        volume_surge = recent_volumes.max() / avg_volume if avg_volume > 0 else 0

        # 거래량 증가 추세
        volume_trend = (recent_volumes.iloc[-1] - recent_volumes.iloc[0]) / recent_volumes.iloc[0] if recent_volumes.iloc[0] > 0 else 0

        return {
            'avg_volume': avg_volume,
            'max_volume_spike': volume_surge,
            'volume_trend': volume_trend,
            'has_volume_surge': volume_surge >= self.volume_surge_multiplier,
        }

    def analyze_price_pattern(self, ohlcv_before: pd.DataFrame) -> Dict:
        """
        급등 전 가격 패턴 분석

        Args:
            ohlcv_before: 급등 전 OHLCV 데이터

        Returns:
            Dict: 가격 패턴 분석 결과
        """
        if ohlcv_before.empty or len(ohlcv_before) < 24:
            return {}

        # 가격 변동성 계산
        returns = ohlcv_before['close'].pct_change().dropna()
        volatility = returns.std() * 100  # 퍼센트로 변환

        # 가격 추세
        price_trend = ((ohlcv_before['close'].iloc[-1] - ohlcv_before['close'].iloc[0]) /
                      ohlcv_before['close'].iloc[0] * 100)

        # 지지/저항 레벨 분석
        recent_high = ohlcv_before.tail(24)['high'].max()
        recent_low = ohlcv_before.tail(24)['low'].min()
        current_price = ohlcv_before['close'].iloc[-1]

        # 저항선 돌파 여부 (최근 고점 대비)
        resistance_break = current_price >= recent_high * 0.98

        # 연속 상승 캔들 개수
        consecutive_greens = 0
        for i in range(len(ohlcv_before) - 1, -1, -1):
            if ohlcv_before.iloc[i]['close'] > ohlcv_before.iloc[i]['open']:
                consecutive_greens += 1
            else:
                break

        return {
            'volatility': volatility,
            'price_trend': price_trend,
            'resistance_break': resistance_break,
            'consecutive_green_candles': consecutive_greens,
            'price_range': (recent_high - recent_low) / recent_low * 100 if recent_low > 0 else 0,
        }

    def analyze_funding_rate_pattern(self, funding_history: pd.DataFrame) -> Dict:
        """
        펀딩비율 패턴 분석

        Args:
            funding_history: 펀딩비율 히스토리 데이터

        Returns:
            Dict: 펀딩비율 패턴 분석 결과
        """
        if funding_history.empty:
            return {}

        recent_funding = funding_history.tail(10)

        # 평균 펀딩비율
        avg_funding = recent_funding['funding_rate'].mean()

        # 펀딩비율 변화 추세
        funding_trend = recent_funding['funding_rate'].iloc[-1] - recent_funding['funding_rate'].iloc[0]

        # 펀딩비율 전환 (음수 -> 양수 또는 그 반대)
        funding_changes = recent_funding['funding_rate'].diff().dropna()
        sign_changes = sum(1 for i in range(len(funding_changes) - 1)
                          if funding_changes.iloc[i] * funding_changes.iloc[i + 1] < 0)

        return {
            'avg_funding_rate': avg_funding,
            'funding_trend': funding_trend,
            'funding_sign_changes': sign_changes,
            'latest_funding_rate': recent_funding['funding_rate'].iloc[-1] if len(recent_funding) > 0 else 0,
        }

    def analyze_comprehensive_pattern(self, surge_details: Dict) -> Dict:
        """
        급등 이벤트의 종합 패턴 분석

        Args:
            surge_details: get_surge_details()에서 반환된 상세 데이터

        Returns:
            Dict: 종합 패턴 분석 결과
        """
        results = {
            'symbol': surge_details.get('symbol'),
            'surge_timestamp': surge_details.get('surge_timestamp'),
        }

        # 거래량 패턴 분석
        ohlcv_before = surge_details.get('ohlcv_before', pd.DataFrame())
        if not ohlcv_before.empty:
            volume_pattern = self.analyze_volume_pattern(ohlcv_before)
            results.update({'volume_' + k: v for k, v in volume_pattern.items()})

            # 가격 패턴 분석
            price_pattern = self.analyze_price_pattern(ohlcv_before)
            results.update({'price_' + k: v for k, v in price_pattern.items()})

        # 펀딩비율 패턴 분석
        funding_history = surge_details.get('funding_history', pd.DataFrame())
        if not funding_history.empty:
            funding_pattern = self.analyze_funding_rate_pattern(funding_history)
            results.update({'funding_' + k: v for k, v in funding_pattern.items()})

        # 미결제약정 데이터
        oi_data = surge_details.get('current_oi', {})
        if oi_data:
            results['open_interest'] = oi_data.get('open_interest', 0)

        # 롱/숏 비율
        ls_ratio = surge_details.get('long_short_ratio', {})
        if ls_ratio:
            results['long_short_ratio'] = ls_ratio.get('long_short_ratio', 0)

        return results

    def find_common_patterns(self, all_patterns: List[Dict]) -> Dict:
        """
        여러 급등 이벤트의 공통 패턴 탐색

        Args:
            all_patterns: 각 급등 이벤트의 패턴 분석 결과 리스트

        Returns:
            Dict: 공통 패턴 통계
        """
        if not all_patterns:
            return {}

        df = pd.DataFrame(all_patterns)

        # 수치형 컬럼만 선택
        numeric_cols = df.select_dtypes(include=[np.number]).columns

        common_patterns = {
            'total_events': len(all_patterns),
            'patterns': {}
        }

        # 각 지표의 평균, 중앙값, 표준편차 계산
        for col in numeric_cols:
            if col in df.columns and not df[col].isna().all():
                common_patterns['patterns'][col] = {
                    'mean': df[col].mean(),
                    'median': df[col].median(),
                    'std': df[col].std(),
                    'min': df[col].min(),
                    'max': df[col].max(),
                }

        # Boolean 패턴 분석
        if 'volume_has_volume_surge' in df.columns:
            common_patterns['volume_surge_rate'] = df['volume_has_volume_surge'].sum() / len(df) * 100

        if 'price_resistance_break' in df.columns:
            common_patterns['resistance_break_rate'] = df['price_resistance_break'].sum() / len(df) * 100

        return common_patterns

    def score_pattern(self, pattern: Dict) -> float:
        """
        패턴 점수화 - 급등 가능성 점수 계산

        Args:
            pattern: 패턴 분석 결과

        Returns:
            float: 0-100 사이의 점수
        """
        score = 0.0

        # 거래량 급증 (30점)
        if pattern.get('volume_has_volume_surge', False):
            score += 30
        elif pattern.get('volume_max_volume_spike', 0) > 1.5:
            score += 15

        # 저항선 돌파 (20점)
        if pattern.get('price_resistance_break', False):
            score += 20

        # 연속 상승 캔들 (15점)
        consecutive_greens = pattern.get('price_consecutive_green_candles', 0)
        if consecutive_greens >= 5:
            score += 15
        elif consecutive_greens >= 3:
            score += 10

        # 가격 추세 (15점)
        price_trend = pattern.get('price_price_trend', 0)
        if price_trend > 5:
            score += 15
        elif price_trend > 2:
            score += 10

        # 펀딩비율 변화 (10점)
        funding_trend = pattern.get('funding_funding_trend', 0)
        if abs(funding_trend) > 0.0001:  # 유의미한 변화
            score += 10

        # 롱/숏 비율 (10점)
        ls_ratio = pattern.get('long_short_ratio', 0)
        if ls_ratio > 1.2:  # 롱 우세
            score += 10
        elif ls_ratio < 0.8:  # 숏 우세 (반등 가능성)
            score += 5

        return min(score, 100)

    def generate_pattern_report(self, common_patterns: Dict) -> str:
        """
        공통 패턴 리포트 생성

        Args:
            common_patterns: find_common_patterns() 결과

        Returns:
            str: 텍스트 리포트
        """
        report = []
        report.append("=" * 60)
        report.append("급등 코인 공통 패턴 분석 리포트")
        report.append("=" * 60)
        report.append(f"\n총 분석 이벤트 수: {common_patterns.get('total_events', 0)}")

        patterns = common_patterns.get('patterns', {})

        # 거래량 패턴
        report.append("\n[거래량 패턴]")
        if 'volume_max_volume_spike' in patterns:
            spike = patterns['volume_max_volume_spike']
            report.append(f"  평균 거래량 급증 배수: {spike['mean']:.2f}x (중앙값: {spike['median']:.2f}x)")

        if 'volume_surge_rate' in common_patterns:
            report.append(f"  거래량 급증 발생률: {common_patterns['volume_surge_rate']:.1f}%")

        # 가격 패턴
        report.append("\n[가격 패턴]")
        if 'price_volatility' in patterns:
            vol = patterns['price_volatility']
            report.append(f"  평균 변동성: {vol['mean']:.2f}% (범위: {vol['min']:.2f}% ~ {vol['max']:.2f}%)")

        if 'price_consecutive_green_candles' in patterns:
            greens = patterns['price_consecutive_green_candles']
            report.append(f"  평균 연속 상승 캔들: {greens['mean']:.1f}개")

        if 'resistance_break_rate' in common_patterns:
            report.append(f"  저항선 돌파율: {common_patterns['resistance_break_rate']:.1f}%")

        # 펀딩비율 패턴
        report.append("\n[펀딩비율 패턴]")
        if 'funding_avg_funding_rate' in patterns:
            funding = patterns['funding_avg_funding_rate']
            report.append(f"  평균 펀딩비율: {funding['mean']:.6f}")

        # 롱/숏 비율
        if 'long_short_ratio' in patterns:
            ls = patterns['long_short_ratio']
            report.append(f"\n[롱/숏 비율]")
            report.append(f"  평균 비율: {ls['mean']:.2f} (중앙값: {ls['median']:.2f})")

        report.append("\n" + "=" * 60)

        return "\n".join(report)
