"""
소셜 데이터 수집 모듈
LunarCrush API를 통해 소셜 미디어 데이터 수집
"""
import requests
import pandas as pd
from datetime import datetime, timedelta
from typing import Dict, List, Optional
import time


class SocialDataCollector:
    """소셜 미디어 데이터 수집기 (LunarCrush)"""

    def __init__(self, api_key: str = ''):
        """
        Args:
            api_key: LunarCrush API 키
        """
        self.api_key = api_key
        self.base_url = 'https://api.lunarcrush.com/v2'
        self.session = requests.Session()

    def get_asset_social_data(self, symbol: str) -> Dict:
        """
        특정 자산의 소셜 데이터 조회

        Args:
            symbol: 코인 심볼 (예: 'BTC', 'ETH')

        Returns:
            Dict: 소셜 미디어 데이터
        """
        if not self.api_key:
            return {}

        try:
            # 심볼 형식 정리 (BTC/USDT:USDT -> BTC)
            clean_symbol = symbol.split('/')[0].replace('USDT', '')

            url = f"{self.base_url}/assets"
            params = {
                'key': self.api_key,
                'symbol': clean_symbol
            }

            response = self.session.get(url, params=params, timeout=10)
            response.raise_for_status()

            data = response.json()

            if data.get('data') and len(data['data']) > 0:
                asset_data = data['data'][0]
                return {
                    'symbol': symbol,
                    'social_volume': asset_data.get('social_volume', 0),
                    'social_volume_change_24h': asset_data.get('social_volume_24h_change', 0),
                    'social_score': asset_data.get('galaxy_score', 0),
                    'sentiment': asset_data.get('average_sentiment', 0),
                    'tweets_24h': asset_data.get('tweets', 0),
                    'social_dominance': asset_data.get('social_dominance', 0),
                    'market_dominance': asset_data.get('market_dominance', 0),
                    'timestamp': datetime.now()
                }

            return {}

        except Exception as e:
            print(f"Error fetching social data for {symbol}: {e}")
            return {}

    def get_trending_coins(self, limit: int = 10) -> List[Dict]:
        """
        소셜 미디어에서 트렌딩 중인 코인 조회

        Args:
            limit: 조회할 코인 개수

        Returns:
            List[Dict]: 트렌딩 코인 리스트
        """
        if not self.api_key:
            return []

        try:
            url = f"{self.base_url}/market"
            params = {
                'key': self.api_key,
                'limit': limit,
                'sort': 'social_volume_24h'
            }

            response = self.session.get(url, params=params, timeout=10)
            response.raise_for_status()

            data = response.json()

            if data.get('data'):
                trending = []
                for item in data['data'][:limit]:
                    trending.append({
                        'symbol': item.get('symbol'),
                        'name': item.get('name'),
                        'social_volume': item.get('social_volume', 0),
                        'social_volume_change_24h': item.get('social_volume_24h_change', 0),
                        'price_change_24h': item.get('percent_change_24h', 0),
                        'sentiment': item.get('average_sentiment', 0),
                        'galaxy_score': item.get('galaxy_score', 0)
                    })
                return trending

            return []

        except Exception as e:
            print(f"Error fetching trending coins: {e}")
            return []

    def get_social_volume_spike(self, symbols: List[str], threshold: float = 50.0) -> pd.DataFrame:
        """
        소셜 볼륨 급증 코인 감지

        Args:
            symbols: 확인할 심볼 리스트
            threshold: 급증 기준 (%, 기본값 50%)

        Returns:
            DataFrame: 소셜 볼륨 급증 코인
        """
        if not self.api_key:
            return pd.DataFrame()

        results = []

        for symbol in symbols:
            social_data = self.get_asset_social_data(symbol)

            if social_data and social_data.get('social_volume_change_24h', 0) >= threshold:
                results.append(social_data)

            time.sleep(0.5)  # API rate limit 방지

        return pd.DataFrame(results)

    def detect_social_patterns(self, symbol: str, days: int = 7) -> Dict:
        """
        소셜 미디어 패턴 감지

        Args:
            symbol: 코인 심볼
            days: 분석 기간 (일)

        Returns:
            Dict: 소셜 패턴 분석 결과
        """
        social_data = self.get_asset_social_data(symbol)

        if not social_data:
            return {}

        patterns = {
            'has_social_spike': social_data.get('social_volume_change_24h', 0) > 50,
            'high_sentiment': social_data.get('sentiment', 0) > 3.5,
            'low_sentiment': social_data.get('sentiment', 0) < 2.5,
            'social_volume': social_data.get('social_volume', 0),
            'social_score': social_data.get('social_score', 0),
            'is_trending': social_data.get('social_dominance', 0) > 1.0
        }

        return patterns


class CoinGeckoCollector:
    """
    CoinGecko API를 통한 추가 데이터 수집
    완전 무료, API 키 불필요
    """

    def __init__(self):
        self.base_url = 'https://api.coingecko.com/api/v3'
        self.session = requests.Session()

    def get_coin_id(self, symbol: str) -> Optional[str]:
        """
        심볼로 CoinGecko coin ID 찾기

        Args:
            symbol: 코인 심볼 (예: 'BTC')

        Returns:
            str: CoinGecko coin ID (예: 'bitcoin')
        """
        # 일반적인 매핑
        symbol_mapping = {
            'BTC': 'bitcoin',
            'ETH': 'ethereum',
            'BNB': 'binancecoin',
            'XRP': 'ripple',
            'ADA': 'cardano',
            'DOGE': 'dogecoin',
            'SOL': 'solana',
            'MATIC': 'matic-network',
            'DOT': 'polkadot',
            'AVAX': 'avalanche-2',
            'LINK': 'chainlink',
            'UNI': 'uniswap',
            'ATOM': 'cosmos',
        }

        clean_symbol = symbol.split('/')[0].replace('USDT', '')
        return symbol_mapping.get(clean_symbol, clean_symbol.lower())

    def get_trending(self) -> List[Dict]:
        """
        CoinGecko 트렌딩 코인 조회

        Returns:
            List[Dict]: 트렌딩 코인 리스트
        """
        try:
            url = f"{self.base_url}/search/trending"
            response = self.session.get(url, timeout=10)
            response.raise_for_status()

            data = response.json()

            trending = []
            if data.get('coins'):
                for item in data['coins']:
                    coin = item.get('item', {})
                    trending.append({
                        'symbol': coin.get('symbol'),
                        'name': coin.get('name'),
                        'market_cap_rank': coin.get('market_cap_rank'),
                        'price_btc': coin.get('price_btc'),
                        'score': coin.get('score', 0)
                    })

            return trending

        except Exception as e:
            print(f"Error fetching CoinGecko trending: {e}")
            return []

    def get_market_data(self, coin_id: str) -> Dict:
        """
        코인 시장 데이터 조회

        Args:
            coin_id: CoinGecko coin ID

        Returns:
            Dict: 시장 데이터
        """
        try:
            url = f"{self.base_url}/coins/{coin_id}"
            params = {
                'localization': 'false',
                'tickers': 'false',
                'community_data': 'true',
                'developer_data': 'false'
            }

            response = self.session.get(url, params=params, timeout=10)
            response.raise_for_status()

            data = response.json()

            market_data = data.get('market_data', {})
            community_data = data.get('community_data', {})

            return {
                'market_cap': market_data.get('market_cap', {}).get('usd', 0),
                'market_cap_rank': data.get('market_cap_rank', 0),
                'total_volume': market_data.get('total_volume', {}).get('usd', 0),
                'price_change_24h': market_data.get('price_change_percentage_24h', 0),
                'price_change_7d': market_data.get('price_change_percentage_7d', 0),
                'twitter_followers': community_data.get('twitter_followers', 0),
                'reddit_subscribers': community_data.get('reddit_subscribers', 0),
                'reddit_avg_posts_48h': community_data.get('reddit_average_posts_48h', 0),
                'reddit_avg_comments_48h': community_data.get('reddit_average_comments_48h', 0),
                'timestamp': datetime.now()
            }

        except Exception as e:
            print(f"Error fetching market data for {coin_id}: {e}")
            return {}
