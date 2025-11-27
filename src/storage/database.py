"""
데이터베이스 저장 모듈
급등 이벤트 및 패턴 분석 결과 저장
"""
import sqlite3
import pandas as pd
from datetime import datetime
from typing import Dict, List, Optional
import json
import os


class SurgeDatabase:
    """급등 분석 데이터베이스"""

    def __init__(self, db_path: str = 'data/surge_analysis.db'):
        """
        Args:
            db_path: 데이터베이스 파일 경로
        """
        # 디렉토리 생성
        os.makedirs(os.path.dirname(db_path), exist_ok=True)

        self.db_path = db_path
        self.conn = sqlite3.connect(db_path)
        self._create_tables()

    def _create_tables(self):
        """데이터베이스 테이블 생성"""
        cursor = self.conn.cursor()

        # 급등 이벤트 테이블
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS surge_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol TEXT NOT NULL,
                surge_timestamp TIMESTAMP NOT NULL,
                surge_change REAL NOT NULL,
                price_before REAL,
                price_after REAL,
                volume REAL,
                detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(symbol, surge_timestamp)
            )
        ''')

        # 패턴 분석 결과 테이블
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS pattern_analysis (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                surge_event_id INTEGER,
                pattern_data TEXT,
                pattern_score REAL,
                analyzed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (surge_event_id) REFERENCES surge_events(id)
            )
        ''')

        # OHLCV 데이터 테이블
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS ohlcv_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol TEXT NOT NULL,
                timestamp TIMESTAMP NOT NULL,
                open REAL,
                high REAL,
                low REAL,
                close REAL,
                volume REAL,
                UNIQUE(symbol, timestamp)
            )
        ''')

        # 펀딩비율 데이터 테이블
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS funding_rate (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol TEXT NOT NULL,
                timestamp TIMESTAMP NOT NULL,
                funding_rate REAL,
                UNIQUE(symbol, timestamp)
            )
        ''')

        # 시가총액 데이터 테이블
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS market_cap_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol TEXT NOT NULL,
                market_cap REAL,
                market_cap_rank INTEGER,
                total_volume REAL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(symbol, updated_at)
            )
        ''')

        # 기술적 지표 테이블
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS technical_indicators (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol TEXT NOT NULL,
                timestamp TIMESTAMP NOT NULL,
                rsi_14 REAL,
                rsi_7 REAL,
                macd REAL,
                macd_signal REAL,
                macd_histogram REAL,
                bb_upper REAL,
                bb_middle REAL,
                bb_lower REAL,
                bb_width REAL,
                ema_9 REAL,
                ema_21 REAL,
                ema_50 REAL,
                atr_14 REAL,
                stoch_k REAL,
                stoch_d REAL,
                obv REAL,
                vwap REAL,
                mfi_14 REAL,
                adx_14 REAL,
                volume_ratio REAL,
                price_position REAL,
                UNIQUE(symbol, timestamp)
            )
        ''')

        # 소셜 데이터 테이블
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS social_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol TEXT NOT NULL,
                timestamp TIMESTAMP NOT NULL,
                social_volume INTEGER,
                social_volume_change_24h REAL,
                social_score REAL,
                sentiment REAL,
                tweets_24h INTEGER,
                social_dominance REAL,
                reddit_subscribers INTEGER,
                reddit_avg_posts_48h REAL,
                UNIQUE(symbol, timestamp)
            )
        ''')

        # ML 모델 메타데이터 테이블
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS ml_models (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                model_name TEXT NOT NULL,
                model_version TEXT NOT NULL,
                model_type TEXT,
                training_samples INTEGER,
                test_accuracy REAL,
                test_precision REAL,
                test_recall REAL,
                test_f1_score REAL,
                feature_count INTEGER,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(model_name, model_version)
            )
        ''')

        # 예측 결과 테이블
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS predictions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol TEXT NOT NULL,
                prediction_timestamp TIMESTAMP NOT NULL,
                predicted_surge_probability REAL,
                pattern_score REAL,
                model_version TEXT,
                actual_surge BOOLEAN,
                surge_happened_at TIMESTAMP,
                actual_surge_change REAL,
                prediction_correct BOOLEAN,
                UNIQUE(symbol, prediction_timestamp)
            )
        ''')

        # 백테스트 결과 테이블
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS backtest_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                backtest_name TEXT NOT NULL,
                model_version TEXT,
                start_date TIMESTAMP,
                end_date TIMESTAMP,
                initial_balance REAL,
                final_balance REAL,
                total_return REAL,
                total_trades INTEGER,
                winning_trades INTEGER,
                losing_trades INTEGER,
                win_rate REAL,
                max_drawdown REAL,
                sharpe_ratio REAL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        ''')

        # 성능 추적 테이블
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS performance_tracking (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tracking_date DATE NOT NULL,
                total_predictions INTEGER,
                correct_predictions INTEGER,
                false_positives INTEGER,
                false_negatives INTEGER,
                accuracy REAL,
                precision REAL,
                recall REAL,
                f1_score REAL,
                UNIQUE(tracking_date)
            )
        ''')

        self.conn.commit()

    def save_surge_event(self, event: Dict) -> int:
        """
        급등 이벤트 저장

        Args:
            event: 급등 이벤트 정보

        Returns:
            int: 저장된 레코드 ID
        """
        cursor = self.conn.cursor()

        try:
            cursor.execute('''
                INSERT OR REPLACE INTO surge_events
                (symbol, surge_timestamp, surge_change, price_before, price_after, volume)
                VALUES (?, ?, ?, ?, ?, ?)
            ''', (
                event.get('symbol'),
                event.get('surge_timestamp'),
                event.get('surge_change'),
                event.get('price_before'),
                event.get('price_after'),
                event.get('volume'),
            ))

            self.conn.commit()
            return cursor.lastrowid

        except Exception as e:
            print(f"Error saving surge event: {e}")
            self.conn.rollback()
            return -1

    def save_pattern_analysis(self, surge_event_id: int, pattern: Dict, score: float):
        """
        패턴 분석 결과 저장

        Args:
            surge_event_id: 급등 이벤트 ID
            pattern: 패턴 분석 결과
            score: 패턴 점수
        """
        cursor = self.conn.cursor()

        try:
            # Dict를 JSON으로 직렬화 (datetime 처리)
            pattern_json = json.dumps(pattern, default=str)

            cursor.execute('''
                INSERT INTO pattern_analysis
                (surge_event_id, pattern_data, pattern_score)
                VALUES (?, ?, ?)
            ''', (surge_event_id, pattern_json, score))

            self.conn.commit()

        except Exception as e:
            print(f"Error saving pattern analysis: {e}")
            self.conn.rollback()

    def save_ohlcv_batch(self, ohlcv_df: pd.DataFrame):
        """
        OHLCV 데이터 일괄 저장

        Args:
            ohlcv_df: OHLCV DataFrame
        """
        try:
            ohlcv_df.to_sql('ohlcv_data', self.conn, if_exists='append', index=False)
            self.conn.commit()
        except Exception as e:
            print(f"Error saving OHLCV data: {e}")
            self.conn.rollback()

    def save_funding_rate_batch(self, funding_df: pd.DataFrame):
        """
        펀딩비율 데이터 일괄 저장

        Args:
            funding_df: 펀딩비율 DataFrame
        """
        try:
            funding_df.to_sql('funding_rate', self.conn, if_exists='append', index=False)
            self.conn.commit()
        except Exception as e:
            print(f"Error saving funding rate data: {e}")
            self.conn.rollback()

    def get_surge_events(self, days: int = 7) -> pd.DataFrame:
        """
        최근 급등 이벤트 조회

        Args:
            days: 조회할 일수

        Returns:
            DataFrame: 급등 이벤트 리스트
        """
        query = '''
            SELECT * FROM surge_events
            WHERE surge_timestamp >= datetime('now', '-{} days')
            ORDER BY surge_timestamp DESC
        '''.format(days)

        return pd.read_sql_query(query, self.conn)

    def get_pattern_analysis(self, surge_event_id: Optional[int] = None) -> pd.DataFrame:
        """
        패턴 분석 결과 조회

        Args:
            surge_event_id: 특정 급등 이벤트 ID (None이면 전체 조회)

        Returns:
            DataFrame: 패턴 분석 결과
        """
        if surge_event_id:
            query = '''
                SELECT * FROM pattern_analysis
                WHERE surge_event_id = ?
            '''
            return pd.read_sql_query(query, self.conn, params=(surge_event_id,))
        else:
            query = 'SELECT * FROM pattern_analysis'
            return pd.read_sql_query(query, self.conn)

    def get_top_scored_patterns(self, limit: int = 10) -> pd.DataFrame:
        """
        높은 점수의 패턴 조회

        Args:
            limit: 조회할 개수

        Returns:
            DataFrame: 상위 패턴
        """
        query = '''
            SELECT p.*, s.symbol, s.surge_timestamp, s.surge_change
            FROM pattern_analysis p
            JOIN surge_events s ON p.surge_event_id = s.id
            ORDER BY p.pattern_score DESC
            LIMIT ?
        '''
        return pd.read_sql_query(query, self.conn, params=(limit,))

    def get_symbol_history(self, symbol: str) -> Dict:
        """
        특정 심볼의 전체 히스토리 조회

        Args:
            symbol: 거래 심볼

        Returns:
            Dict: 급등 이벤트, OHLCV, 펀딩비율 데이터
        """
        surge_events = pd.read_sql_query(
            'SELECT * FROM surge_events WHERE symbol = ? ORDER BY surge_timestamp',
            self.conn,
            params=(symbol,)
        )

        ohlcv = pd.read_sql_query(
            'SELECT * FROM ohlcv_data WHERE symbol = ? ORDER BY timestamp',
            self.conn,
            params=(symbol,)
        )

        funding = pd.read_sql_query(
            'SELECT * FROM funding_rate WHERE symbol = ? ORDER BY timestamp',
            self.conn,
            params=(symbol,)
        )

        return {
            'surge_events': surge_events,
            'ohlcv': ohlcv,
            'funding_rate': funding,
        }

    def export_to_csv(self, table_name: str, output_path: str):
        """
        테이블을 CSV로 내보내기

        Args:
            table_name: 테이블 이름
            output_path: 출력 파일 경로
        """
        try:
            df = pd.read_sql_query(f'SELECT * FROM {table_name}', self.conn)
            df.to_csv(output_path, index=False)
            print(f"Exported {table_name} to {output_path}")
        except Exception as e:
            print(f"Error exporting to CSV: {e}")

    def close(self):
        """데이터베이스 연결 종료"""
        self.conn.close()
