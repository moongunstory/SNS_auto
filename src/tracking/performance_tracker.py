"""
성능 추적 시스템
예측 정확도 및 실시간 성능 모니터링
"""
import pandas as pd
import numpy as np
from typing import Dict
from datetime import datetime, timedelta

from src.storage.database import SurgeDatabase
from src.collector.binance_collector import BinanceCollector


class PerformanceTracker:
    """예측 성능 추적 및 분석"""

    def __init__(self, database: SurgeDatabase, binance_collector: BinanceCollector):
        """
        Args:
            database: 데이터베이스
            binance_collector: 바이낸스 데이터 수집기
        """
        self.db = database
        self.collector = binance_collector

    def verify_predictions(self, hours_to_verify: int = 24,
                          surge_threshold: float = 10.0) -> int:
        """
        과거 예측 결과 검증

        Args:
            hours_to_verify: 예측 후 검증할 시간 (시간)
            surge_threshold: 급등 기준 (%)

        Returns:
            int: 검증된 예측 수
        """
        print("예측 결과 검증 중...")

        # 검증 가능한 예측들 조회 (24시간 이상 경과)
        cutoff_time = datetime.now() - timedelta(hours=hours_to_verify)

        query = '''
            SELECT *
            FROM predictions
            WHERE prediction_timestamp < ?
            AND actual_surge IS NULL
        '''

        df = pd.read_sql_query(query, self.db.conn, params=(cutoff_time,))

        if df.empty:
            print("검증할 예측이 없습니다.")
            return 0

        print(f"{len(df)}개 예측 검증 중...")

        verified_count = 0

        for idx, row in df.iterrows():
            symbol = row['symbol']
            pred_time = pd.to_datetime(row['prediction_timestamp'])

            # 예측 후 24시간 데이터 조회
            ohlcv = self.collector.get_historical_ohlcv(symbol, timeframe='1h', days=3)

            if ohlcv.empty:
                continue

            # 예측 시점 이후 데이터
            after_prediction = ohlcv[ohlcv['timestamp'] > pred_time].head(hours_to_verify)

            if len(after_prediction) < hours_to_verify:
                continue

            # 최대 상승률 계산
            entry_price = after_prediction.iloc[0]['close']
            max_price = after_prediction['high'].max()
            max_return = ((max_price - entry_price) / entry_price) * 100

            # 실제 급등 여부
            actual_surge = max_return >= surge_threshold

            # 예측 정확도
            predicted_surge = row['predicted_surge_probability'] >= 0.7
            prediction_correct = (predicted_surge == actual_surge)

            # DB 업데이트
            cursor = self.db.conn.cursor()
            cursor.execute('''
                UPDATE predictions
                SET actual_surge = ?,
                    actual_surge_change = ?,
                    prediction_correct = ?,
                    surge_happened_at = ?
                WHERE id = ?
            ''', (
                actual_surge,
                max_return,
                prediction_correct,
                after_prediction.iloc[after_prediction['high'].argmax()]['timestamp'] if actual_surge else None,
                row['id']
            ))

            verified_count += 1

        self.db.conn.commit()

        print(f"{verified_count}개 예측 검증 완료")

        return verified_count

    def get_daily_performance(self, days: int = 7) -> pd.DataFrame:
        """
        일별 성능 통계

        Args:
            days: 조회 기간 (일)

        Returns:
            DataFrame: 일별 성능
        """
        query = f'''
            SELECT
                DATE(prediction_timestamp) as date,
                COUNT(*) as total_predictions,
                SUM(CASE WHEN actual_surge IS NOT NULL THEN 1 ELSE 0 END) as verified,
                SUM(CASE WHEN prediction_correct = 1 THEN 1 ELSE 0 END) as correct,
                SUM(CASE WHEN prediction_correct = 0 THEN 1 ELSE 0 END) as incorrect,
                AVG(predicted_surge_probability) as avg_probability
            FROM predictions
            WHERE prediction_timestamp >= datetime('now', '-{days} days')
            GROUP BY DATE(prediction_timestamp)
            ORDER BY date DESC
        '''

        df = pd.read_sql_query(query, self.db.conn)

        if not df.empty:
            df['accuracy'] = (df['correct'] / df['verified'] * 100).fillna(0)

        return df

    def get_overall_performance(self) -> Dict:
        """
        전체 성능 통계

        Returns:
            Dict: 전체 성능 메트릭
        """
        query = '''
            SELECT
                COUNT(*) as total_predictions,
                SUM(CASE WHEN actual_surge IS NOT NULL THEN 1 ELSE 0 END) as verified,
                SUM(CASE WHEN prediction_correct = 1 THEN 1 ELSE 0 END) as correct,
                SUM(CASE WHEN actual_surge = 1 AND prediction_correct = 1 THEN 1 ELSE 0 END) as true_positives,
                SUM(CASE WHEN actual_surge = 0 AND prediction_correct = 0 THEN 1 ELSE 0 END) as false_positives,
                SUM(CASE WHEN actual_surge = 1 AND prediction_correct = 0 THEN 1 ELSE 0 END) as false_negatives,
                SUM(CASE WHEN actual_surge = 0 AND prediction_correct = 1 THEN 1 ELSE 0 END) as true_negatives,
                AVG(predicted_surge_probability) as avg_probability,
                AVG(CASE WHEN actual_surge = 1 THEN actual_surge_change ELSE NULL END) as avg_surge_change
            FROM predictions
            WHERE actual_surge IS NOT NULL
        '''

        result = pd.read_sql_query(query, self.db.conn).iloc[0].to_dict()

        # 메트릭 계산
        total = result['total_predictions']
        verified = result['verified']
        correct = result['correct']

        accuracy = (correct / verified * 100) if verified > 0 else 0

        tp = result['true_positives'] or 0
        fp = result['false_positives'] or 0
        fn = result['false_negatives'] or 0

        precision = (tp / (tp + fp) * 100) if (tp + fp) > 0 else 0
        recall = (tp / (tp + fn) * 100) if (tp + fn) > 0 else 0
        f1_score = (2 * precision * recall / (precision + recall)) if (precision + recall) > 0 else 0

        return {
            'total_predictions': int(total),
            'verified_predictions': int(verified),
            'correct_predictions': int(correct),
            'accuracy': accuracy,
            'precision': precision,
            'recall': recall,
            'f1_score': f1_score,
            'avg_probability': result['avg_probability'],
            'avg_surge_change': result['avg_surge_change']
        }

    def print_performance_report(self, days: int = 7):
        """성능 리포트 출력"""
        print("\n" + "="*60)
        print("성능 추적 리포트")
        print("="*60)

        # 전체 성능
        overall = self.get_overall_performance()

        print("\n[전체 성능]")
        print(f"총 예측 수: {overall['total_predictions']}")
        print(f"검증 완료: {overall['verified_predictions']}")
        print(f"정확도: {overall['accuracy']:.2f}%")
        print(f"정밀도: {overall['precision']:.2f}%")
        print(f"재현율: {overall['recall']:.2f}%")
        print(f"F1-Score: {overall['f1_score']:.2f}%")
        print(f"평균 예측 확률: {overall['avg_probability']:.2f}")

        if overall['avg_surge_change']:
            print(f"실제 급등 시 평균 상승률: {overall['avg_surge_change']:.2f}%")

        # 일별 성능
        daily = self.get_daily_performance(days)

        if not daily.empty:
            print(f"\n[최근 {days}일 일별 성능]")
            print("-" * 60)
            for _, row in daily.iterrows():
                print(f"{row['date']}: "
                      f"예측 {int(row['total_predictions'])}개 | "
                      f"검증 {int(row['verified'])}개 | "
                      f"정확도 {row['accuracy']:.1f}%")

        print("="*60)
