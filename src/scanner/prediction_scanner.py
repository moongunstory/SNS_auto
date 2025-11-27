"""
실시간 예측 스캐너
학습된 패턴으로 급등 가능성이 높은 코인 실시간 탐지
"""
import pandas as pd
import numpy as np
from typing import Dict, List
from datetime import datetime
import time

from src.collector.binance_collector import BinanceCollector
from src.ml.pattern_learner import PatternLearner
from src.storage.database import SurgeDatabase


class PredictionScanner:
    """실시간 급등 예측 스캐너"""

    def __init__(self, binance_collector: BinanceCollector,
                 pattern_learner: PatternLearner,
                 database: SurgeDatabase,
                 min_probability: float = 0.7):
        """
        Args:
            binance_collector: 바이낸스 데이터 수집기
            pattern_learner: 학습된 패턴 모델
            database: 데이터베이스
            min_probability: 알림 최소 확률
        """
        self.collector = binance_collector
        self.learner = pattern_learner
        self.db = database
        self.min_probability = min_probability

        self.alert_history = {}  # 중복 알림 방지

    def scan_all_symbols(self, top_n: int = 10) -> List[Dict]:
        """
        전체 선물 코인 스캔하여 급등 가능성 높은 코인 찾기

        Args:
            top_n: 상위 N개 코인만 반환

        Returns:
            List[Dict]: 급등 예측 결과 (확률 높은 순)
        """
        print("="*60)
        print(f"실시간 급등 예측 스캔 시작 - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print("="*60)

        # 모든 USDT 선물 심볼 조회
        symbols = self.collector.get_all_usdt_futures()
        print(f"총 {len(symbols)}개 코인 스캔 중...\n")

        predictions = []

        for i, symbol in enumerate(symbols, 1):
            if i % 50 == 0:
                print(f"진행: {i}/{len(symbols)}")

            try:
                # 최근 100시간 데이터 수집
                ohlcv = self.collector.get_historical_ohlcv(symbol, timeframe='1h', days=5)

                if ohlcv.empty or len(ohlcv) < 100:
                    continue

                # 급등 예측
                result = self.learner.predict_single(ohlcv)

                if result['success'] and result['surge_probability'] >= self.min_probability:
                    predictions.append({
                        'symbol': symbol,
                        'probability': result['surge_probability'],
                        'will_surge': result['will_surge'],
                        'confidence': result['confidence'],
                        'current_price': ohlcv.iloc[-1]['close'],
                        'timestamp': datetime.now()
                    })

                time.sleep(0.1)  # API rate limit 방지

            except Exception as e:
                continue

        # 확률 높은 순 정렬
        predictions.sort(key=lambda x: x['probability'], reverse=True)

        # 상위 N개만 반환
        top_predictions = predictions[:top_n]

        # 출력
        if top_predictions:
            print(f"\n급등 가능성 높은 코인 TOP {len(top_predictions)}:")
            print("-" * 80)
            for i, pred in enumerate(top_predictions, 1):
                print(f"{i}. {pred['symbol']:20s} | 확률: {pred['probability']*100:5.1f}% | "
                      f"신뢰도: {pred['confidence']:8s} | 가격: ${pred['current_price']:.4f}")
            print("-" * 80)
        else:
            print("\n현재 급등 가능성이 높은 코인이 없습니다.")

        return top_predictions

    def monitor_specific_symbols(self, symbols: List[str],
                                interval_seconds: int = 300) -> None:
        """
        특정 코인들을 지속적으로 모니터링

        Args:
            symbols: 모니터링할 심볼 리스트
            interval_seconds: 스캔 간격 (초)
        """
        print("="*60)
        print(f"실시간 모니터링 시작 - {len(symbols)}개 코인")
        print("="*60)
        print(f"스캔 간격: {interval_seconds}초")
        print(f"최소 확률: {self.min_probability*100:.1f}%")
        print("\n모니터링 중... (Ctrl+C로 종료)\n")

        try:
            while True:
                timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                print(f"[{timestamp}] 스캔 중...")

                for symbol in symbols:
                    try:
                        # 데이터 수집
                        ohlcv = self.collector.get_historical_ohlcv(symbol, timeframe='1h', days=5)

                        if ohlcv.empty or len(ohlcv) < 100:
                            continue

                        # 예측
                        result = self.learner.predict_single(ohlcv)

                        if not result['success']:
                            continue

                        probability = result['surge_probability']

                        # 알림 조건 확인
                        if probability >= self.min_probability:
                            # 중복 알림 방지 (1시간 내 같은 코인 재알림 안 함)
                            last_alert_time = self.alert_history.get(symbol)

                            if last_alert_time is None or \
                               (datetime.now() - last_alert_time).seconds > 3600:

                                self._send_alert(symbol, probability, ohlcv.iloc[-1]['close'])
                                self.alert_history[symbol] = datetime.now()

                                # DB에 저장
                                self._save_prediction(symbol, probability, result)

                    except Exception as e:
                        print(f"  오류 ({symbol}): {e}")
                        continue

                print(f"완료. {interval_seconds}초 후 다시 스캔합니다.\n")
                time.sleep(interval_seconds)

        except KeyboardInterrupt:
            print("\n\n모니터링 종료")

    def _send_alert(self, symbol: str, probability: float, current_price: float):
        """알림 전송"""
        print("\n" + "🚨"*20)
        print(f"⚠️  급등 가능성 감지!")
        print("-" * 60)
        print(f"코인: {symbol}")
        print(f"급등 확률: {probability*100:.1f}%")
        print(f"현재 가격: ${current_price:.4f}")
        print(f"시간: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print("🚨"*20 + "\n")

    def _save_prediction(self, symbol: str, probability: float, prediction_result: Dict):
        """예측 결과 DB 저장"""
        try:
            cursor = self.db.conn.cursor()
            cursor.execute('''
                INSERT OR REPLACE INTO predictions
                (symbol, prediction_timestamp, predicted_surge_probability, model_version)
                VALUES (?, ?, ?, ?)
            ''', (
                symbol,
                datetime.now(),
                probability,
                self.learner.model_type
            ))
            self.db.conn.commit()
        except Exception as e:
            print(f"예측 저장 오류: {e}")

    def get_prediction_performance(self, days: int = 7) -> Dict:
        """
        최근 예측 성능 확인

        Args:
            days: 확인할 기간 (일)

        Returns:
            Dict: 성능 통계
        """
        query = f'''
            SELECT *
            FROM predictions
            WHERE prediction_timestamp >= datetime('now', '-{days} days')
        '''

        df = pd.read_sql_query(query, self.db.conn)

        if df.empty:
            return {
                'total_predictions': 0,
                'verified_predictions': 0
            }

        # 실제 급등 여부 확인 (간단 버전)
        verified_count = df['actual_surge'].notna().sum()
        correct_count = df[df['prediction_correct'] == True].shape[0]

        accuracy = (correct_count / verified_count * 100) if verified_count > 0 else 0

        return {
            'total_predictions': len(df),
            'verified_predictions': int(verified_count),
            'correct_predictions': int(correct_count),
            'accuracy': accuracy,
            'avg_probability': df['predicted_surge_probability'].mean()
        }
