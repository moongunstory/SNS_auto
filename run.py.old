#!/usr/bin/env python3
"""
바이낸스 선물 급등 코인 패턴 분석 도구
메인 실행 스크립트
"""
import sys
import os
from datetime import datetime
from tabulate import tabulate

# 프로젝트 루트를 Python 경로에 추가
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from src.collector.binance_collector import BinanceCollector
from src.collector.market_data_collector import MarketDataCollector
from src.scanner.surge_scanner import SurgeScanner
from src.analyzer.pattern_analyzer import PatternAnalyzer
from src.storage.database import SurgeDatabase
from src.visualizer.chart_generator import ChartGenerator
from config.config import *


def print_header(title):
    """헤더 출력"""
    print("\n" + "=" * 80)
    print(f"  {title}")
    print("=" * 80 + "\n")


def scan_current_surges():
    """현재 급등 코인 스캔"""
    print_header("현재 급등 코인 스캔")

    collector = BinanceCollector(BINANCE_API_KEY, BINANCE_SECRET_KEY)
    scanner = SurgeScanner(collector, SURGE_THRESHOLD)

    # 현재 급등 코인 조회
    surged_coins = scanner.scan_current_surges()

    if surged_coins.empty:
        print("현재 급등 중인 코인이 없습니다.")
        return

    # 상위 20개만 표시
    top_surges = surged_coins.head(20)

    # 테이블 형식으로 출력
    table_data = []
    for _, row in top_surges.iterrows():
        table_data.append([
            row['symbol'],
            f"{row['change_24h']:.2f}%",
            f"${row['price']:.4f}",
            f"${row['volume_24h']:,.0f}"
        ])

    print(tabulate(
        table_data,
        headers=['심볼', '24시간 변동률', '현재가', '거래량'],
        tablefmt='grid'
    ))

    return surged_coins


def analyze_historical_surges(days=7, max_analyze=10):
    """과거 급등 이벤트 분석"""
    print_header(f"최근 {days}일 급등 코인 패턴 분석")

    # 초기화
    collector = BinanceCollector(BINANCE_API_KEY, BINANCE_SECRET_KEY)
    scanner = SurgeScanner(collector, SURGE_THRESHOLD)
    analyzer = PatternAnalyzer(VOLUME_SURGE_MULTIPLIER)
    db = SurgeDatabase(DB_PATH)
    chart_gen = ChartGenerator(CHART_DIR)

    # 과거 급등 이벤트 스캔
    print("급등 이벤트 스캔 중...")
    surge_events = scanner.scan_historical_surges(days=days)

    if not surge_events:
        print("급등 이벤트를 찾지 못했습니다.")
        return

    print(f"\n총 {len(surge_events)}개의 급등 이벤트 발견\n")

    # 상위 이벤트만 상세 분석
    events_to_analyze = surge_events[:max_analyze]
    all_patterns = []

    print(f"상위 {max_analyze}개 이벤트 상세 분석 중...\n")

    for i, event in enumerate(events_to_analyze, 1):
        symbol = event['symbol']
        surge_timestamp = event['surge_timestamp']

        print(f"[{i}/{max_analyze}] {symbol} 분석 중 (급등률: {event['surge_change']:.2f}%)...")

        try:
            # 급등 이벤트 DB 저장
            surge_id = db.save_surge_event(event)

            # 상세 데이터 수집
            surge_details = scanner.get_surge_details(
                symbol,
                surge_timestamp,
                LOOKBACK_HOURS_BEFORE_SURGE
            )

            if not surge_details:
                print(f"  - 데이터 수집 실패")
                continue

            # 패턴 분석
            pattern = analyzer.analyze_comprehensive_pattern(surge_details)
            if pattern:
                # 패턴 점수 계산
                score = analyzer.score_pattern(pattern)
                pattern['pattern_score'] = score

                all_patterns.append(pattern)

                # DB에 저장
                db.save_pattern_analysis(surge_id, pattern, score)

                print(f"  - 패턴 점수: {score:.1f}/100")

                # 차트 생성 (상위 3개만)
                if i <= 3:
                    ohlcv_before = surge_details.get('ohlcv_before')
                    ohlcv_after = surge_details.get('ohlcv_after')

                    if not ohlcv_before.empty and not ohlcv_after.empty:
                        chart_path = chart_gen.plot_surge_with_volume(
                            symbol, ohlcv_before, ohlcv_after
                        )
                        print(f"  - 차트 저장: {chart_path}")

        except Exception as e:
            print(f"  - 오류: {e}")
            continue

    # 공통 패턴 분석
    if all_patterns:
        print("\n공통 패턴 분석 중...")
        common_patterns = analyzer.find_common_patterns(all_patterns)

        # 리포트 생성
        report = analyzer.generate_pattern_report(common_patterns)
        print("\n" + report)

        # 리포트 파일 저장
        os.makedirs(OUTPUT_DIR, exist_ok=True)
        report_path = os.path.join(
            OUTPUT_DIR,
            f"pattern_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
        )
        with open(report_path, 'w', encoding='utf-8') as f:
            f.write(report)
        print(f"\n리포트 저장: {report_path}")

        # 레이더 차트 생성
        radar_path = chart_gen.plot_common_patterns_radar(common_patterns)
        print(f"공통 패턴 차트 저장: {radar_path}")

    db.close()
    print("\n분석 완료!")


def live_monitor():
    """실시간 모니터링 모드"""
    print_header("실시간 급등 모니터링")

    collector = BinanceCollector(BINANCE_API_KEY, BINANCE_SECRET_KEY)
    scanner = SurgeScanner(collector, SURGE_THRESHOLD)
    analyzer = PatternAnalyzer(VOLUME_SURGE_MULTIPLIER)

    print(f"급등 기준: {SURGE_THRESHOLD}% 이상")
    print("모니터링 시작... (Ctrl+C로 종료)\n")

    import time
    previous_surges = set()

    try:
        while True:
            surged_coins = scanner.scan_current_surges()

            if not surged_coins.empty:
                current_surges = set(surged_coins['symbol'])
                new_surges = current_surges - previous_surges

                if new_surges:
                    print(f"\n[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] 새로운 급등 발견!")

                    for symbol in new_surges:
                        row = surged_coins[surged_coins['symbol'] == symbol].iloc[0]
                        print(f"  {symbol}: +{row['change_24h']:.2f}% (${row['price']:.4f})")

                        # 간단한 패턴 체크
                        try:
                            data = collector.get_comprehensive_data(symbol, days=3)
                            if data['ohlcv'] is not None and not data['ohlcv'].empty:
                                volume_pattern = analyzer.analyze_volume_pattern(
                                    data['ohlcv'].tail(72)
                                )
                                if volume_pattern.get('has_volume_surge'):
                                    print(f"    ⚠️  거래량 급증 감지!")

                        except Exception as e:
                            pass

                previous_surges = current_surges

            # 1분마다 체크
            time.sleep(60)

    except KeyboardInterrupt:
        print("\n\n모니터링 종료")


def show_menu():
    """메뉴 표시"""
    print("\n" + "=" * 80)
    print("  바이낸스 선물 급등 코인 패턴 분석 도구")
    print("=" * 80)
    print("\n메뉴:")
    print("  1. 현재 급등 코인 스캔")
    print("  2. 과거 급등 이벤트 패턴 분석 (7일)")
    print("  3. 실시간 모니터링")
    print("  4. 저장된 패턴 분석 결과 보기")
    print("  5. 종료")
    print()


def show_saved_patterns():
    """저장된 패턴 분석 결과 보기"""
    print_header("저장된 패턴 분석 결과")

    db = SurgeDatabase(DB_PATH)

    # 상위 점수 패턴 조회
    top_patterns = db.get_top_scored_patterns(limit=10)

    if top_patterns.empty:
        print("저장된 패턴이 없습니다.")
        db.close()
        return

    # 테이블 형식으로 출력
    table_data = []
    for _, row in top_patterns.iterrows():
        table_data.append([
            row['symbol'],
            row['surge_timestamp'],
            f"{row['surge_change']:.2f}%",
            f"{row['pattern_score']:.1f}/100"
        ])

    print(tabulate(
        table_data,
        headers=['심볼', '급등 시점', '급등률', '패턴 점수'],
        tablefmt='grid'
    ))

    db.close()


def main():
    """메인 함수"""
    while True:
        show_menu()

        try:
            choice = input("선택: ").strip()

            if choice == '1':
                scan_current_surges()

            elif choice == '2':
                analyze_historical_surges(days=SCAN_DAYS, max_analyze=10)

            elif choice == '3':
                live_monitor()

            elif choice == '4':
                show_saved_patterns()

            elif choice == '5':
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
