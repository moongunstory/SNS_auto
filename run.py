#!/usr/bin/env python3
"""
바이낸스 선물 급등 패턴 학습 및 예측 시스템
메인 실행 스크립트
"""
import sys
import os
from datetime import datetime
from tabulate import tabulate

# 프로젝트 루트를 Python 경로에 추가
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from src.collector.binance_collector import BinanceCollector
from src.scanner.surge_scanner import SurgeScanner
from src.analyzer.pattern_analyzer import PatternAnalyzer
from src.storage.database import SurgeDatabase
from src.ml.pattern_learner import PatternLearner
from src.ml.feature_engineer import FeatureEngineer
from src.backtesting.backtest_engine import BacktestEngine
from src.scanner.prediction_scanner import PredictionScanner
from src.tracking.performance_tracker import PerformanceTracker
from config.config import *


def print_header(title):
    """헤더 출력"""
    print("\n" + "=" * 80)
    print(f"  {title}")
    print("=" * 80 + "\n")


def collect_and_train_model(days=180, max_samples=200):
    """
    1. 과거 급등 데이터 수집
    2. Feature 생성
    3. 모델 학습
    """
    print_header(f"급등 패턴 학습 - 과거 {days}일 데이터")

    collector = BinanceCollector(BINANCE_API_KEY, BINANCE_SECRET_KEY)
    scanner = SurgeScanner(collector, SURGE_THRESHOLD)
    db = SurgeDatabase(DB_PATH)
    feature_engineer = FeatureEngineer()

    # 1. 급등 이벤트 스캔
    print(f"STEP 1: 과거 {days}일간 급등 이벤트 스캔 중...")
    surge_events = scanner.scan_historical_surges(days=days)

    if len(surge_events) < MIN_SURGE_SAMPLES:
        print(f"\n경고: 급등 샘플이 부족합니다 ({len(surge_events)}/{MIN_SURGE_SAMPLES})")
        print("더 긴 기간을 스캔하거나 급등 기준을 낮춰보세요.")
        return

    print(f"급등 이벤트 {len(surge_events)}개 발견")

    # 2. 상위 샘플만 사용
    surge_events = surge_events[:min(max_samples, len(surge_events))]

    # 3. 각 이벤트의 상세 데이터 수집
    print(f"\nSTEP 2: 상위 {len(surge_events)}개 이벤트 상세 데이터 수집 중...")

    ohlcv_data = {}
    valid_surge_events = []

    for i, event in enumerate(surge_events, 1):
        if i % 20 == 0:
            print(f"  진행: {i}/{len(surge_events)}")

        symbol = event['symbol']
        surge_time = event['surge_timestamp']

        try:
            surge_details = scanner.get_surge_details(
                symbol, surge_time, LOOKBACK_HOURS_BEFORE_SURGE
            )

            if surge_details and not surge_details['ohlcv_before'].empty:
                ohlcv_data[f"{symbol}_{i}"] = surge_details['ohlcv_before']
                valid_surge_events.append(event)

                # DB 저장
                db.save_surge_event(event)

        except Exception as e:
            continue

    print(f"유효한 급등 샘플: {len(valid_surge_events)}개")

    # 4. 비급등 샘플 생성 (클래스 균형)
    print("\nSTEP 3: 비급등 샘플 생성 중...")
    non_surge_samples = []

    # 급등하지 않은 랜덤 시점 샘플링
    import random
    from datetime import timedelta

    for event in valid_surge_events[:len(valid_surge_events)//2]:
        symbol = event['symbol']

        try:
            # 급등 시점 전 일주일 사이의 랜덤 시점
            base_time = event['surge_timestamp']
            random_offset = random.randint(7*24, 14*24)  # 7-14일 전
            random_time = base_time - timedelta(hours=random_offset)

            ohlcv = collector.get_historical_ohlcv(symbol, timeframe='1h', days=30)
            if not ohlcv.empty:
                before_random = ohlcv[ohlcv['timestamp'] < random_time].tail(100)

                if len(before_random) >= 50:
                    ohlcv_data[f"{symbol}_non_surge_{len(non_surge_samples)}"] = before_random
                    non_surge_samples.append({
                        'symbol': symbol,
                        'timestamp': random_time
                    })

        except:
            continue

    print(f"비급등 샘플: {len(non_surge_samples)}개")

    # 5. Feature 생성
    print("\nSTEP 4: Feature 엔지니어링...")
    training_data = feature_engineer.prepare_training_data(
        valid_surge_events,
        non_surge_samples,
        ohlcv_data
    )

    if training_data.empty:
        print("Feature 생성 실패")
        return

    print(f"학습 데이터 생성 완료: {len(training_data)}개 샘플")

    # 6. 모델 학습
    print("\nSTEP 5: 머신러닝 모델 학습...")

    learner = PatternLearner(model_type='xgboost')
    metrics = learner.train(training_data, test_size=TRAIN_TEST_SPLIT_RATIO, use_smote=True)

    # 7. 모델 저장
    model_path = os.path.join(MODEL_DIR, MODEL_FILE)
    learner.save_model(model_path)

    db.close()

    print("\n학습 완료!")
    print(f"모델 저장 위치: {model_path}")


def predict_surge_coins():
    """실시간 급등 예측 스캔"""
    print_header("실시간 급등 예측 스캔")

    # 모델 로드
    model_path = os.path.join(MODEL_DIR, MODEL_FILE)

    if not os.path.exists(model_path):
        print("학습된 모델이 없습니다. 먼저 '1. 급등 패턴 학습'을 실행하세요.")
        return

    collector = BinanceCollector(BINANCE_API_KEY, BINANCE_SECRET_KEY)
    db = SurgeDatabase(DB_PATH)

    learner = PatternLearner()
    learner.load_model(model_path)

    scanner = PredictionScanner(
        collector,
        learner,
        db,
        min_probability=PREDICTION_MIN_PROBABILITY
    )

    # 전체 코인 스캔
    predictions = scanner.scan_all_symbols(top_n=MAX_CONCURRENT_ALERTS)

    db.close()


def live_prediction_monitor():
    """실시간 급등 예측 모니터링"""
    print_header("실시간 급등 예측 모니터링")

    # 모델 로드
    model_path = os.path.join(MODEL_DIR, MODEL_FILE)

    if not os.path.exists(model_path):
        print("학습된 모델이 없습니다. 먼저 '1. 급등 패턴 학습'을 실행하세요.")
        return

    collector = BinanceCollector(BINANCE_API_KEY, BINANCE_SECRET_KEY)
    db = SurgeDatabase(DB_PATH)

    learner = PatternLearner()
    learner.load_model(model_path)

    scanner = PredictionScanner(
        collector,
        learner,
        db,
        min_probability=PREDICTION_MIN_PROBABILITY
    )

    # 모든 USDT 선물 코인 모니터링
    symbols = collector.get_all_usdt_futures()

    scanner.monitor_specific_symbols(symbols, interval_seconds=PREDICTION_SCAN_INTERVAL)

    db.close()


def run_backtest():
    """백테스팅 실행"""
    print_header("백테스팅")

    # TODO: 실제 백테스팅 구현
    print("백테스팅 기능 준비 중...")


def view_performance():
    """성능 추적 리포트"""
    print_header("성능 추적 리포트")

    db = SurgeDatabase(DB_PATH)
    collector = BinanceCollector(BINANCE_API_KEY, BINANCE_SECRET_KEY)

    tracker = PerformanceTracker(db, collector)

    # 예측 검증
    print("최근 예측 검증 중...\n")
    verified_count = tracker.verify_predictions(hours_to_verify=24)

    # 성능 리포트 출력
    tracker.print_performance_report(days=7)

    db.close()


def show_menu():
    """메뉴 표시"""
    print("\n" + "=" * 80)
    print("  🚀 바이낸스 선물 급등 패턴 학습 및 예측 시스템")
    print("=" * 80)
    print("\n[데이터 학습]")
    print("  1. 급등 패턴 학습 (과거 데이터 수집 + ML 모델 학습)")
    print("\n[실시간 예측]")
    print("  2. 실시간 급등 예측 스캔 (1회)")
    print("  3. 실시간 급등 예측 모니터링 (지속)")
    print("\n[분석 및 평가]")
    print("  4. 백테스팅 (과거 데이터로 성능 검증)")
    print("  5. 성능 추적 리포트 (예측 정확도 확인)")
    print("\n  0. 종료")
    print()


def main():
    """메인 함수"""
    while True:
        show_menu()

        try:
            choice = input("선택: ").strip()

            if choice == '1':
                days = input(f"스캔 기간 (일, 기본값 {SCAN_DAYS}): ").strip()
                days = int(days) if days else SCAN_DAYS
                collect_and_train_model(days=days)

            elif choice == '2':
                predict_surge_coins()

            elif choice == '3':
                live_prediction_monitor()

            elif choice == '4':
                run_backtest()

            elif choice == '5':
                view_performance()

            elif choice == '0':
                print("\n프로그램을 종료합니다.")
                break

            else:
                print("\n잘못된 선택입니다. 다시 선택해주세요.")

        except KeyboardInterrupt:
            print("\n\n프로그램을 종료합니다.")
            break
        except Exception as e:
            print(f"\n오류 발생: {e}")
            import traceback
            traceback.print_exc()


if __name__ == '__main__':
    main()
