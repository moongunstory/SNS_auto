"""
백테스팅 엔진
과거 데이터로 모델 성능 검증
"""
import pandas as pd
import numpy as np
from typing import Dict, List
from datetime import datetime, timedelta


class BacktestEngine:
    """백테스팅 엔진"""

    def __init__(self, initial_balance: float = 10000,
                 position_size: float = 0.1,
                 stop_loss: float = 0.05,
                 take_profit: float = 0.15):
        """
        Args:
            initial_balance: 초기 자본 (USDT)
            position_size: 포지션 크기 (자본의 비율, 0-1)
            stop_loss: 손절 비율 (0-1)
            take_profit: 익절 비율 (0-1)
        """
        self.initial_balance = initial_balance
        self.position_size = position_size
        self.stop_loss = stop_loss
        self.take_profit = take_profit

        self.balance = initial_balance
        self.trades = []
        self.equity_curve = []

    def run_backtest(self, predictions: pd.DataFrame, historical_prices: pd.DataFrame,
                    min_probability: float = 0.7) -> Dict:
        """
        백테스트 실행

        Args:
            predictions: 예측 결과 (symbol, timestamp, probability 포함)
            historical_prices: 과거 가격 데이터 (symbol, timestamp, open, high, low, close)
            min_probability: 진입 최소 확률

        Returns:
            Dict: 백테스트 결과
        """
        print("="*60)
        print("백테스트 시작")
        print("="*60)
        print(f"초기 자본: ${self.initial_balance:,.2f}")
        print(f"포지션 크기: {self.position_size*100:.1f}%")
        print(f"손절: {self.stop_loss*100:.1f}%, 익절: {self.take_profit*100:.1f}%")
        print(f"최소 진입 확률: {min_probability*100:.1f}%\n")

        # 예측 필터링 (확률 기준)
        strong_predictions = predictions[predictions['probability'] >= min_probability].copy()

        print(f"총 예측 수: {len(predictions)}")
        print(f"진입 조건 충족: {len(strong_predictions)}\n")

        for idx, pred in strong_predictions.iterrows():
            symbol = pred['symbol']
            pred_time = pred['timestamp']
            probability = pred['probability']

            # 해당 시점의 가격 데이터 찾기
            price_at_prediction = historical_prices[
                (historical_prices['symbol'] == symbol) &
                (historical_prices['timestamp'] >= pred_time)
            ].head(1)

            if price_at_prediction.empty:
                continue

            entry_price = price_at_prediction.iloc[0]['close']
            entry_time = price_at_prediction.iloc[0]['timestamp']

            # 진입 후 24시간 가격 추적
            future_prices = historical_prices[
                (historical_prices['symbol'] == symbol) &
                (historical_prices['timestamp'] > entry_time) &
                (historical_prices['timestamp'] <= entry_time + timedelta(hours=24))
            ]

            if future_prices.empty:
                continue

            # 거래 시뮬레이션
            trade_result = self._simulate_trade(
                entry_price, future_prices, symbol, entry_time, probability
            )

            if trade_result:
                self.trades.append(trade_result)
                self.balance += trade_result['pnl']
                self.equity_curve.append({
                    'timestamp': trade_result['exit_time'],
                    'balance': self.balance
                })

        # 결과 계산
        results = self._calculate_results()

        return results

    def _simulate_trade(self, entry_price: float, future_prices: pd.DataFrame,
                       symbol: str, entry_time: datetime, probability: float) -> Dict:
        """
        단일 거래 시뮬레이션

        Args:
            entry_price: 진입 가격
            future_prices: 진입 후 가격 데이터
            symbol: 심볼
            entry_time: 진입 시간
            probability: 예측 확률

        Returns:
            Dict: 거래 결과
        """
        position_value = self.balance * self.position_size
        quantity = position_value / entry_price

        stop_loss_price = entry_price * (1 - self.stop_loss)
        take_profit_price = entry_price * (1 + self.take_profit)

        exit_price = None
        exit_time = None
        exit_reason = None

        # 각 캔들에서 손절/익절 확인
        for _, row in future_prices.iterrows():
            # 손절 확인
            if row['low'] <= stop_loss_price:
                exit_price = stop_loss_price
                exit_time = row['timestamp']
                exit_reason = 'stop_loss'
                break

            # 익절 확인
            if row['high'] >= take_profit_price:
                exit_price = take_profit_price
                exit_time = row['timestamp']
                exit_reason = 'take_profit'
                break

        # 손절/익절 안 걸리면 마지막 가격으로 청산
        if exit_price is None:
            exit_price = future_prices.iloc[-1]['close']
            exit_time = future_prices.iloc[-1]['timestamp']
            exit_reason = 'timeout'

        # PnL 계산
        pnl = (exit_price - entry_price) * quantity
        pnl_percent = ((exit_price - entry_price) / entry_price) * 100

        return {
            'symbol': symbol,
            'entry_time': entry_time,
            'exit_time': exit_time,
            'entry_price': entry_price,
            'exit_price': exit_price,
            'quantity': quantity,
            'pnl': pnl,
            'pnl_percent': pnl_percent,
            'exit_reason': exit_reason,
            'probability': probability
        }

    def _calculate_results(self) -> Dict:
        """백테스트 결과 계산"""
        if not self.trades:
            return {
                'total_trades': 0,
                'final_balance': self.initial_balance,
                'total_return': 0,
                'win_rate': 0
            }

        df_trades = pd.DataFrame(self.trades)

        # 기본 통계
        total_trades = len(df_trades)
        winning_trades = len(df_trades[df_trades['pnl'] > 0])
        losing_trades = len(df_trades[df_trades['pnl'] <= 0])

        win_rate = (winning_trades / total_trades) * 100 if total_trades > 0 else 0

        total_return = ((self.balance - self.initial_balance) / self.initial_balance) * 100

        avg_win = df_trades[df_trades['pnl'] > 0]['pnl_percent'].mean() if winning_trades > 0 else 0
        avg_loss = df_trades[df_trades['pnl'] <= 0]['pnl_percent'].mean() if losing_trades > 0 else 0

        # 최대 손실 (Drawdown)
        if self.equity_curve:
            equity_df = pd.DataFrame(self.equity_curve)
            equity_df['peak'] = equity_df['balance'].cummax()
            equity_df['drawdown'] = (equity_df['balance'] - equity_df['peak']) / equity_df['peak'] * 100
            max_drawdown = equity_df['drawdown'].min()
        else:
            max_drawdown = 0

        # Sharpe Ratio (간소화 버전)
        if len(df_trades) > 1:
            returns = df_trades['pnl_percent'].values
            sharpe_ratio = (returns.mean() / returns.std()) * np.sqrt(252) if returns.std() != 0 else 0
        else:
            sharpe_ratio = 0

        # 출력
        print("\n" + "="*60)
        print("백테스트 결과")
        print("="*60)
        print(f"\n총 거래 수: {total_trades}")
        print(f"승리 거래: {winning_trades} ({win_rate:.1f}%)")
        print(f"손실 거래: {losing_trades} ({100-win_rate:.1f}%)")
        print(f"\n초기 자본: ${self.initial_balance:,.2f}")
        print(f"최종 자본: ${self.balance:,.2f}")
        print(f"총 수익률: {total_return:+.2f}%")
        print(f"\n평균 승리: {avg_win:+.2f}%")
        print(f"평균 손실: {avg_loss:+.2f}%")
        print(f"최대 낙폭: {max_drawdown:.2f}%")
        print(f"Sharpe Ratio: {sharpe_ratio:.2f}")

        # 청산 사유별 통계
        print("\n청산 사유별 통계:")
        for reason in df_trades['exit_reason'].unique():
            count = len(df_trades[df_trades['exit_reason'] == reason])
            print(f"  {reason}: {count} ({count/total_trades*100:.1f}%)")

        results = {
            'total_trades': total_trades,
            'winning_trades': winning_trades,
            'losing_trades': losing_trades,
            'win_rate': win_rate,
            'initial_balance': self.initial_balance,
            'final_balance': self.balance,
            'total_return': total_return,
            'avg_win': avg_win,
            'avg_loss': avg_loss,
            'max_drawdown': max_drawdown,
            'sharpe_ratio': sharpe_ratio,
            'trades': self.trades,
            'equity_curve': self.equity_curve
        }

        return results

    def reset(self):
        """백테스트 상태 초기화"""
        self.balance = self.initial_balance
        self.trades = []
        self.equity_curve = []
