"""
외부 마켓 데이터 수집 모듈 (시가총액, 펀더멘탈 등)
"""
import requests
import pandas as pd
from typing import Dict, List
import time


class MarketDataCollector:
    """CoinGecko API를 통한 시가총액 및 기타 데이터 수집"""

    def __init__(self):
        self.base_url = "https://api.coingecko.com/api/v3"
        self.session = requests.Session()

    def _convert_symbol_to_coingecko_id(self, symbol: str) -> str:
        """
        바이낸스 심볼을 CoinGecko ID로 변환

        Args:
            symbol: 바이낸스 심볼 (예: 'BTC/USDT:USDT')

        Returns:
            str: CoinGecko ID (예: 'bitcoin')
        """
        # 심볼에서 기본 티커 추출
        base = symbol.split('/')[0].upper()

        # 주요 코인 매핑
        mapping = {
            'BTC': 'bitcoin',
            'ETH': 'ethereum',
            'BNB': 'binancecoin',
            'SOL': 'solana',
            'XRP': 'ripple',
            'ADA': 'cardano',
            'DOGE': 'dogecoin',
            'MATIC': 'matic-network',
            'DOT': 'polkadot',
            'AVAX': 'avalanche-2',
            'LINK': 'chainlink',
            'ATOM': 'cosmos',
            'UNI': 'uniswap',
            'LTC': 'litecoin',
            'BCH': 'bitcoin-cash',
            'NEAR': 'near',
            'APT': 'aptos',
            'ARB': 'arbitrum',
            'OP': 'optimism',
            'INJ': 'injective-protocol',
        }

        # 매핑에 있으면 반환, 없으면 소문자로 변환하여 시도
        return mapping.get(base, base.lower())

    def get_coin_market_data(self, symbol: str) -> Dict:
        """
        코인의 시가총액 및 기타 마켓 데이터 조회

        Args:
            symbol: 바이낸스 심볼

        Returns:
            Dict: market_cap, total_volume, circulating_supply 등 포함
        """
        try:
            coin_id = self._convert_symbol_to_coingecko_id(symbol)

            url = f"{self.base_url}/coins/{coin_id}"
            params = {
                'localization': 'false',
                'tickers': 'false',
                'market_data': 'true',
                'community_data': 'false',
                'developer_data': 'false'
            }

            response = self.session.get(url, params=params, timeout=10)

            if response.status_code == 200:
                data = response.json()
                market_data = data.get('market_data', {})

                return {
                    'symbol': symbol,
                    'coin_id': coin_id,
                    'name': data.get('name', ''),
                    'market_cap_usd': market_data.get('market_cap', {}).get('usd', 0),
                    'market_cap_rank': market_data.get('market_cap_rank', 0),
                    'total_volume_usd': market_data.get('total_volume', {}).get('usd', 0),
                    'circulating_supply': market_data.get('circulating_supply', 0),
                    'total_supply': market_data.get('total_supply', 0),
                    'max_supply': market_data.get('max_supply', 0),
                    'ath_usd': market_data.get('ath', {}).get('usd', 0),
                    'ath_change_percentage': market_data.get('ath_change_percentage', {}).get('usd', 0),
                    'atl_usd': market_data.get('atl', {}).get('usd', 0),
                    'atl_change_percentage': market_data.get('atl_change_percentage', {}).get('usd', 0),
                }
            else:
                print(f"Error fetching data for {symbol}: HTTP {response.status_code}")
                return {}

        except Exception as e:
            print(f"Error fetching market data for {symbol}: {e}")
            return {}

    def get_batch_market_data(self, symbols: List[str]) -> pd.DataFrame:
        """
        여러 코인의 시가총액 데이터를 일괄 조회

        Args:
            symbols: 바이낸스 심볼 리스트

        Returns:
            DataFrame: 시가총액 및 기타 데이터
        """
        data_list = []

        for symbol in symbols:
            market_data = self.get_coin_market_data(symbol)
            if market_data:
                data_list.append(market_data)

            # API Rate limit 방지 (CoinGecko 무료 플랜: 분당 10-50 요청)
            time.sleep(1.5)

        return pd.DataFrame(data_list)

    def get_trending_coins(self) -> List[Dict]:
        """
        CoinGecko 트렌딩 코인 조회

        Returns:
            List[Dict]: 트렌딩 코인 정보
        """
        try:
            url = f"{self.base_url}/search/trending"
            response = self.session.get(url, timeout=10)

            if response.status_code == 200:
                data = response.json()
                coins = data.get('coins', [])

                trending = []
                for item in coins:
                    coin = item.get('item', {})
                    trending.append({
                        'name': coin.get('name', ''),
                        'symbol': coin.get('symbol', ''),
                        'market_cap_rank': coin.get('market_cap_rank', 0),
                        'price_btc': coin.get('price_btc', 0),
                    })

                return trending

        except Exception as e:
            print(f"Error fetching trending coins: {e}")

        return []
