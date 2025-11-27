"""
바이낸스 선물 거래소 데이터 수집 모듈
"""
import ccxt
import pandas as pd
from datetime import datetime, timedelta
from typing import List, Dict, Optional
import time


class BinanceCollector:
    """바이낸스 선물 시장 데이터 수집기"""

    def __init__(self, api_key: str = '', secret_key: str = ''):
        """
        Args:
            api_key: 바이낸스 API 키 (선택사항, 공개 데이터는 불필요)
            secret_key: 바이낸스 Secret 키 (선택사항)
        """
        self.exchange = ccxt.binance({
            'apiKey': api_key,
            'secret': secret_key,
            'enableRateLimit': True,
            'options': {
                'defaultType': 'future',  # 선물 시장 사용
            }
        })

    def get_all_usdt_futures(self) -> List[str]:
        """모든 USDT 선물 거래 심볼 조회"""
        try:
            markets = self.exchange.load_markets()
            # USDT 마진 선물만 필터링
            usdt_futures = [
                symbol for symbol, market in markets.items()
                if market.get('quote') == 'USDT'
                and market.get('type') == 'future'
                and market.get('active', False)
            ]
            return usdt_futures
        except Exception as e:
            print(f"Error fetching futures symbols: {e}")
            return []

    def get_24h_price_change(self, symbols: Optional[List[str]] = None) -> pd.DataFrame:
        """
        24시간 가격 변동률 조회

        Args:
            symbols: 조회할 심볼 리스트 (None이면 전체 조회)

        Returns:
            DataFrame: symbol, price_change_percent, volume 등 포함
        """
        try:
            tickers = self.exchange.fetch_tickers()

            data = []
            for symbol, ticker in tickers.items():
                # USDT 선물만 필터링
                if not symbol.endswith('/USDT:USDT'):
                    continue

                if symbols and symbol not in symbols:
                    continue

                data.append({
                    'symbol': symbol,
                    'price': ticker.get('last', 0),
                    'change_24h': ticker.get('percentage', 0),
                    'volume_24h': ticker.get('quoteVolume', 0),
                    'high_24h': ticker.get('high', 0),
                    'low_24h': ticker.get('low', 0),
                    'timestamp': datetime.now()
                })

            df = pd.DataFrame(data)
            return df.sort_values('change_24h', ascending=False)

        except Exception as e:
            print(f"Error fetching 24h price change: {e}")
            return pd.DataFrame()

    def get_historical_ohlcv(self, symbol: str, timeframe: str = '1h',
                            days: int = 7) -> pd.DataFrame:
        """
        과거 OHLCV 데이터 조회

        Args:
            symbol: 거래 심볼 (예: 'BTC/USDT:USDT')
            timeframe: 캔들 간격 ('1m', '5m', '15m', '1h', '4h', '1d')
            days: 조회할 일수

        Returns:
            DataFrame: timestamp, open, high, low, close, volume 포함
        """
        try:
            since = int((datetime.now() - timedelta(days=days)).timestamp() * 1000)

            all_ohlcv = []
            while True:
                ohlcv = self.exchange.fetch_ohlcv(
                    symbol,
                    timeframe=timeframe,
                    since=since,
                    limit=1000
                )

                if not ohlcv:
                    break

                all_ohlcv.extend(ohlcv)

                # 다음 배치를 위해 since 업데이트
                since = ohlcv[-1][0] + 1

                # 현재 시간까지 도달하면 중단
                if ohlcv[-1][0] >= int(datetime.now().timestamp() * 1000):
                    break

                time.sleep(0.5)  # Rate limit 방지

            df = pd.DataFrame(
                all_ohlcv,
                columns=['timestamp', 'open', 'high', 'low', 'close', 'volume']
            )
            df['timestamp'] = pd.to_datetime(df['timestamp'], unit='ms')
            df['symbol'] = symbol

            return df

        except Exception as e:
            print(f"Error fetching OHLCV for {symbol}: {e}")
            return pd.DataFrame()

    def get_funding_rate(self, symbol: str) -> Dict:
        """
        현재 펀딩 비율 조회

        Args:
            symbol: 거래 심볼

        Returns:
            Dict: fundingRate, fundingTimestamp 포함
        """
        try:
            funding = self.exchange.fetch_funding_rate(symbol)
            return {
                'symbol': symbol,
                'funding_rate': funding.get('fundingRate', 0),
                'funding_timestamp': funding.get('fundingTimestamp', None),
                'timestamp': datetime.now()
            }
        except Exception as e:
            print(f"Error fetching funding rate for {symbol}: {e}")
            return {}

    def get_funding_rate_history(self, symbol: str, days: int = 7) -> pd.DataFrame:
        """
        과거 펀딩 비율 히스토리 조회

        Args:
            symbol: 거래 심볼
            days: 조회할 일수

        Returns:
            DataFrame: timestamp, funding_rate 포함
        """
        try:
            since = int((datetime.now() - timedelta(days=days)).timestamp() * 1000)

            funding_history = self.exchange.fetch_funding_rate_history(
                symbol,
                since=since,
                limit=1000
            )

            data = []
            for item in funding_history:
                data.append({
                    'timestamp': pd.to_datetime(item['timestamp'], unit='ms'),
                    'funding_rate': item.get('fundingRate', 0),
                    'symbol': symbol
                })

            return pd.DataFrame(data)

        except Exception as e:
            print(f"Error fetching funding rate history for {symbol}: {e}")
            return pd.DataFrame()

    def get_open_interest(self, symbol: str) -> Dict:
        """
        미결제약정 (Open Interest) 조회

        Args:
            symbol: 거래 심볼

        Returns:
            Dict: open_interest, timestamp 포함
        """
        try:
            oi = self.exchange.fetch_open_interest(symbol)
            return {
                'symbol': symbol,
                'open_interest': oi.get('openInterestAmount', 0),
                'timestamp': datetime.now()
            }
        except Exception as e:
            print(f"Error fetching open interest for {symbol}: {e}")
            return {}

    def get_long_short_ratio(self, symbol: str) -> Dict:
        """
        롱/숏 비율 조회 (바이낸스 공개 API 사용)

        Args:
            symbol: 거래 심볼 (예: 'BTCUSDT')

        Returns:
            Dict: long_short_ratio, long_account, short_account 포함
        """
        try:
            # 심볼 형식 변환 (BTC/USDT:USDT -> BTCUSDT)
            clean_symbol = symbol.replace('/USDT:USDT', 'USDT')

            # 바이낸스 API 직접 호출 (ccxt에서 지원하지 않는 엔드포인트)
            url = f"https://fapi.binance.com/futures/data/globalLongShortAccountRatio"
            params = {
                'symbol': clean_symbol,
                'period': '5m',
                'limit': 1
            }

            import requests
            response = requests.get(url, params=params)
            data = response.json()

            if data:
                latest = data[0]
                return {
                    'symbol': symbol,
                    'long_short_ratio': float(latest['longShortRatio']),
                    'long_account': float(latest['longAccount']),
                    'short_account': float(latest['shortAccount']),
                    'timestamp': pd.to_datetime(latest['timestamp'], unit='ms')
                }

            return {}

        except Exception as e:
            print(f"Error fetching long/short ratio for {symbol}: {e}")
            return {}

    def get_comprehensive_data(self, symbol: str, days: int = 7) -> Dict[str, pd.DataFrame]:
        """
        특정 심볼의 모든 데이터 수집

        Args:
            symbol: 거래 심볼
            days: 조회할 일수

        Returns:
            Dict: 'ohlcv', 'funding_rate', 'current_data' 키를 가진 딕셔너리
        """
        print(f"Collecting comprehensive data for {symbol}...")

        data = {
            'ohlcv': self.get_historical_ohlcv(symbol, timeframe='1h', days=days),
            'funding_rate_history': self.get_funding_rate_history(symbol, days=days),
            'current_funding': self.get_funding_rate(symbol),
            'current_oi': self.get_open_interest(symbol),
            'long_short_ratio': self.get_long_short_ratio(symbol)
        }

        return data
