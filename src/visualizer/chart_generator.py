"""
시각화 모듈
급등 패턴 및 분석 결과 시각화
"""
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
import pandas as pd
from typing import Dict, List
import os


class ChartGenerator:
    """차트 생성 및 시각화"""

    def __init__(self, output_dir: str = 'data/charts'):
        """
        Args:
            output_dir: 차트 저장 디렉토리
        """
        self.output_dir = output_dir
        os.makedirs(output_dir, exist_ok=True)

        # 한글 폰트 설정 (한글이 깨지지 않도록)
        plt.rcParams['font.family'] = 'DejaVu Sans'
        plt.rcParams['axes.unicode_minus'] = False

    def plot_surge_with_volume(self, symbol: str, ohlcv_before: pd.DataFrame,
                               ohlcv_after: pd.DataFrame, save: bool = True) -> str:
        """
        급등 전후 가격 및 거래량 차트

        Args:
            symbol: 거래 심볼
            ohlcv_before: 급등 전 OHLCV 데이터
            ohlcv_after: 급등 후 OHLCV 데이터
            save: 차트 저장 여부

        Returns:
            str: 저장된 파일 경로
        """
        # 데이터 결합
        ohlcv_all = pd.concat([ohlcv_before, ohlcv_after])

        fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10), sharex=True)

        # 가격 차트
        ax1.plot(ohlcv_all['timestamp'], ohlcv_all['close'], label='Close Price', linewidth=2)
        ax1.axvline(ohlcv_after['timestamp'].iloc[0], color='red', linestyle='--',
                   label='Surge Point', linewidth=2)
        ax1.set_ylabel('Price (USDT)', fontsize=12)
        ax1.set_title(f'{symbol} - Price and Volume Analysis', fontsize=14, fontweight='bold')
        ax1.legend()
        ax1.grid(True, alpha=0.3)

        # 거래량 차트
        colors = ['green' if ohlcv_all.iloc[i]['close'] >= ohlcv_all.iloc[i]['open']
                 else 'red' for i in range(len(ohlcv_all))]
        ax2.bar(ohlcv_all['timestamp'], ohlcv_all['volume'], color=colors, alpha=0.6)
        ax2.axvline(ohlcv_after['timestamp'].iloc[0], color='red', linestyle='--', linewidth=2)
        ax2.set_ylabel('Volume', fontsize=12)
        ax2.set_xlabel('Time', fontsize=12)
        ax2.grid(True, alpha=0.3)

        # X축 날짜 포맷
        ax2.xaxis.set_major_formatter(mdates.DateFormatter('%m-%d %H:%M'))
        plt.xticks(rotation=45)

        plt.tight_layout()

        if save:
            filename = f"{symbol.replace('/', '_')}_{pd.Timestamp.now().strftime('%Y%m%d_%H%M%S')}.png"
            filepath = os.path.join(self.output_dir, filename)
            plt.savefig(filepath, dpi=150, bbox_inches='tight')
            plt.close()
            return filepath

        plt.show()
        return ""

    def plot_funding_rate(self, symbol: str, funding_history: pd.DataFrame,
                         surge_timestamp: pd.Timestamp = None, save: bool = True) -> str:
        """
        펀딩비율 차트

        Args:
            symbol: 거래 심볼
            funding_history: 펀딩비율 히스토리
            surge_timestamp: 급등 시점 (표시용)
            save: 차트 저장 여부

        Returns:
            str: 저장된 파일 경로
        """
        fig, ax = plt.subplots(figsize=(14, 6))

        ax.plot(funding_history['timestamp'], funding_history['funding_rate'],
               linewidth=2, marker='o', markersize=4)

        if surge_timestamp:
            ax.axvline(surge_timestamp, color='red', linestyle='--',
                      label='Surge Point', linewidth=2)

        ax.axhline(0, color='black', linestyle='-', linewidth=0.5)
        ax.set_ylabel('Funding Rate', fontsize=12)
        ax.set_xlabel('Time', fontsize=12)
        ax.set_title(f'{symbol} - Funding Rate History', fontsize=14, fontweight='bold')
        ax.legend()
        ax.grid(True, alpha=0.3)

        # X축 날짜 포맷
        ax.xaxis.set_major_formatter(mdates.DateFormatter('%m-%d %H:%M'))
        plt.xticks(rotation=45)

        plt.tight_layout()

        if save:
            filename = f"{symbol.replace('/', '_')}_funding_{pd.Timestamp.now().strftime('%Y%m%d_%H%M%S')}.png"
            filepath = os.path.join(self.output_dir, filename)
            plt.savefig(filepath, dpi=150, bbox_inches='tight')
            plt.close()
            return filepath

        plt.show()
        return ""

    def plot_pattern_scores(self, patterns_df: pd.DataFrame, save: bool = True) -> str:
        """
        패턴 점수 분포 차트

        Args:
            patterns_df: 패턴 분석 결과 DataFrame
            save: 차트 저장 여부

        Returns:
            str: 저장된 파일 경로
        """
        if 'pattern_score' not in patterns_df.columns:
            print("No pattern_score column found")
            return ""

        fig, ax = plt.subplots(figsize=(12, 6))

        # 점수별 히스토그램
        ax.hist(patterns_df['pattern_score'], bins=20, edgecolor='black', alpha=0.7)
        ax.set_xlabel('Pattern Score', fontsize=12)
        ax.set_ylabel('Frequency', fontsize=12)
        ax.set_title('Pattern Score Distribution', fontsize=14, fontweight='bold')
        ax.grid(True, alpha=0.3)

        # 평균 점수 표시
        mean_score = patterns_df['pattern_score'].mean()
        ax.axvline(mean_score, color='red', linestyle='--',
                  label=f'Mean: {mean_score:.2f}', linewidth=2)
        ax.legend()

        plt.tight_layout()

        if save:
            filename = f"pattern_scores_{pd.Timestamp.now().strftime('%Y%m%d_%H%M%S')}.png"
            filepath = os.path.join(self.output_dir, filename)
            plt.savefig(filepath, dpi=150, bbox_inches='tight')
            plt.close()
            return filepath

        plt.show()
        return ""

    def plot_common_patterns_radar(self, common_patterns: Dict, save: bool = True) -> str:
        """
        공통 패턴 레이더 차트

        Args:
            common_patterns: find_common_patterns() 결과
            save: 차트 저장 여부

        Returns:
            str: 저장된 파일 경로
        """
        patterns = common_patterns.get('patterns', {})

        # 주요 지표 선택 및 정규화
        indicators = {
            'Volume Surge': patterns.get('volume_max_volume_spike', {}).get('mean', 0) / 5,  # 5배로 정규화
            'Price Volatility': min(patterns.get('price_volatility', {}).get('mean', 0) / 10, 1),
            'Consecutive Greens': min(patterns.get('price_consecutive_green_candles', {}).get('mean', 0) / 10, 1),
            'Funding Rate': abs(patterns.get('funding_avg_funding_rate', {}).get('mean', 0)) * 10000,
        }

        # 유효한 지표만 선택
        indicators = {k: min(v, 1) for k, v in indicators.items() if v > 0}

        if not indicators:
            print("No valid indicators for radar chart")
            return ""

        # 레이더 차트 생성
        categories = list(indicators.keys())
        values = list(indicators.values())
        values += values[:1]  # 첫 값을 마지막에 추가하여 폐곡선 만들기

        angles = [n / float(len(categories)) * 2 * 3.14159 for n in range(len(categories))]
        angles += angles[:1]

        fig, ax = plt.subplots(figsize=(10, 10), subplot_kw=dict(projection='polar'))
        ax.plot(angles, values, 'o-', linewidth=2, label='Common Pattern')
        ax.fill(angles, values, alpha=0.25)
        ax.set_xticks(angles[:-1])
        ax.set_xticklabels(categories)
        ax.set_ylim(0, 1)
        ax.set_title('Common Pattern Characteristics', size=14, fontweight='bold', pad=20)
        ax.legend(loc='upper right')
        ax.grid(True)

        plt.tight_layout()

        if save:
            filename = f"common_patterns_radar_{pd.Timestamp.now().strftime('%Y%m%d_%H%M%S')}.png"
            filepath = os.path.join(self.output_dir, filename)
            plt.savefig(filepath, dpi=150, bbox_inches='tight')
            plt.close()
            return filepath

        plt.show()
        return ""

    def plot_top_surges(self, surge_events: pd.DataFrame, top_n: int = 20,
                       save: bool = True) -> str:
        """
        상위 급등 코인 막대 차트

        Args:
            surge_events: 급등 이벤트 DataFrame
            top_n: 표시할 개수
            save: 차트 저장 여부

        Returns:
            str: 저장된 파일 경로
        """
        if 'surge_change' not in surge_events.columns:
            print("No surge_change column found")
            return ""

        top_surges = surge_events.nlargest(top_n, 'surge_change')

        fig, ax = plt.subplots(figsize=(12, 8))

        colors = plt.cm.RdYlGn(top_surges['surge_change'] / top_surges['surge_change'].max())
        ax.barh(range(len(top_surges)), top_surges['surge_change'], color=colors)
        ax.set_yticks(range(len(top_surges)))
        ax.set_yticklabels(top_surges['symbol'])
        ax.set_xlabel('24h Change (%)', fontsize=12)
        ax.set_title(f'Top {top_n} Surge Coins', fontsize=14, fontweight='bold')
        ax.grid(True, alpha=0.3, axis='x')

        # 값 표시
        for i, v in enumerate(top_surges['surge_change']):
            ax.text(v + 0.5, i, f'{v:.1f}%', va='center')

        plt.tight_layout()

        if save:
            filename = f"top_surges_{pd.Timestamp.now().strftime('%Y%m%d_%H%M%S')}.png"
            filepath = os.path.join(self.output_dir, filename)
            plt.savefig(filepath, dpi=150, bbox_inches='tight')
            plt.close()
            return filepath

        plt.show()
        return ""
