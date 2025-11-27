"""
패턴 학습 모듈
머신러닝을 통한 급등 패턴 학습 및 예측
"""
import pandas as pd
import numpy as np
from typing import Dict, List, Tuple, Optional
import pickle
import os
from datetime import datetime

from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier
from sklearn.metrics import (
    accuracy_score, precision_score, recall_score, f1_score,
    confusion_matrix, classification_report, roc_auc_score
)
from sklearn.preprocessing import StandardScaler
from imblearn.over_sampling import SMOTE

import xgboost as xgb
import lightgbm as lgb

from src.ml.feature_engineer import FeatureEngineer


class PatternLearner:
    """급등 패턴 학습 엔진"""

    def __init__(self, model_type: str = 'xgboost'):
        """
        Args:
            model_type: 사용할 모델 ('random_forest', 'xgboost', 'lightgbm', 'gradient_boosting')
        """
        self.model_type = model_type
        self.model = None
        self.scaler = StandardScaler()
        self.feature_engineer = FeatureEngineer()
        self.feature_names = []
        self.feature_importance = {}
        self.training_metrics = {}

    def _create_model(self):
        """모델 생성"""
        if self.model_type == 'random_forest':
            return RandomForestClassifier(
                n_estimators=200,
                max_depth=15,
                min_samples_split=10,
                min_samples_leaf=4,
                random_state=42,
                n_jobs=-1
            )
        elif self.model_type == 'xgboost':
            return xgb.XGBClassifier(
                n_estimators=200,
                max_depth=7,
                learning_rate=0.05,
                subsample=0.8,
                colsample_bytree=0.8,
                random_state=42,
                n_jobs=-1,
                eval_metric='logloss'
            )
        elif self.model_type == 'lightgbm':
            return lgb.LGBMClassifier(
                n_estimators=200,
                max_depth=7,
                learning_rate=0.05,
                subsample=0.8,
                colsample_bytree=0.8,
                random_state=42,
                n_jobs=-1,
                verbose=-1
            )
        elif self.model_type == 'gradient_boosting':
            return GradientBoostingClassifier(
                n_estimators=200,
                max_depth=7,
                learning_rate=0.05,
                subsample=0.8,
                random_state=42
            )
        else:
            raise ValueError(f"Unknown model type: {self.model_type}")

    def train(self, training_data: pd.DataFrame, test_size: float = 0.2,
             use_smote: bool = True) -> Dict:
        """
        모델 학습

        Args:
            training_data: 학습 데이터 (Features + Target)
            test_size: 테스트 데이터 비율
            use_smote: SMOTE 오버샘플링 사용 여부

        Returns:
            Dict: 학습 결과 메트릭
        """
        print("="*60)
        print("모델 학습 시작")
        print("="*60)

        # Feature와 Target 분리
        exclude_cols = ['target', 'symbol', 'timestamp']
        feature_cols = [col for col in training_data.columns if col not in exclude_cols]

        X = training_data[feature_cols]
        y = training_data['target']

        # NaN 처리
        X = X.fillna(X.median())

        # Feature 이름 저장
        self.feature_names = feature_cols

        print(f"\n총 샘플 수: {len(X)}")
        print(f"급등 샘플: {y.sum()} ({y.sum()/len(y)*100:.1f}%)")
        print(f"비급등 샘플: {len(y) - y.sum()} ({(len(y)-y.sum())/len(y)*100:.1f}%)")
        print(f"Feature 개수: {len(feature_cols)}")

        # 학습/테스트 데이터 분할
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=test_size, random_state=42, stratify=y
        )

        # 특성 스케일링
        X_train_scaled = self.scaler.fit_transform(X_train)
        X_test_scaled = self.scaler.transform(X_test)

        # SMOTE 오버샘플링 (클래스 불균형 해결)
        if use_smote and y_train.sum() < len(y_train) * 0.4:
            print("\nSMOTE 오버샘플링 적용 중...")
            smote = SMOTE(random_state=42)
            X_train_scaled, y_train = smote.fit_resample(X_train_scaled, y_train)
            print(f"오버샘플링 후 - 급등: {y_train.sum()}, 비급등: {len(y_train) - y_train.sum()}")

        # 모델 생성 및 학습
        print(f"\n{self.model_type} 모델 학습 중...")
        self.model = self._create_model()
        self.model.fit(X_train_scaled, y_train)

        # 예측
        y_train_pred = self.model.predict(X_train_scaled)
        y_test_pred = self.model.predict(X_test_scaled)

        # 확률 예측
        y_train_proba = self.model.predict_proba(X_train_scaled)[:, 1]
        y_test_proba = self.model.predict_proba(X_test_scaled)[:, 1]

        # 평가
        train_metrics = self._evaluate_model(y_train, y_train_pred, y_train_proba, "Training")
        test_metrics = self._evaluate_model(y_test, y_test_pred, y_test_proba, "Test")

        # Feature Importance
        self._calculate_feature_importance()

        # 결과 저장
        self.training_metrics = {
            'train': train_metrics,
            'test': test_metrics,
            'feature_importance': self.feature_importance,
            'n_features': len(feature_cols),
            'n_train_samples': len(X_train),
            'n_test_samples': len(X_test),
            'model_type': self.model_type,
            'trained_at': datetime.now()
        }

        print("\n모델 학습 완료!")

        return self.training_metrics

    def _evaluate_model(self, y_true: np.ndarray, y_pred: np.ndarray,
                       y_proba: np.ndarray, dataset_name: str) -> Dict:
        """모델 평가"""
        accuracy = accuracy_score(y_true, y_pred)
        precision = precision_score(y_true, y_pred, zero_division=0)
        recall = recall_score(y_true, y_pred, zero_division=0)
        f1 = f1_score(y_true, y_pred, zero_division=0)

        try:
            auc = roc_auc_score(y_true, y_proba)
        except:
            auc = 0.0

        cm = confusion_matrix(y_true, y_pred)

        print(f"\n{dataset_name} 결과:")
        print(f"  Accuracy:  {accuracy:.4f}")
        print(f"  Precision: {precision:.4f}")
        print(f"  Recall:    {recall:.4f}")
        print(f"  F1-Score:  {f1:.4f}")
        print(f"  AUC:       {auc:.4f}")
        print(f"\n혼동 행렬:")
        print(f"  TN: {cm[0][0]}, FP: {cm[0][1]}")
        print(f"  FN: {cm[1][0]}, TP: {cm[1][1]}")

        return {
            'accuracy': accuracy,
            'precision': precision,
            'recall': recall,
            'f1_score': f1,
            'auc': auc,
            'confusion_matrix': cm.tolist()
        }

    def _calculate_feature_importance(self):
        """Feature Importance 계산"""
        if hasattr(self.model, 'feature_importances_'):
            importances = self.model.feature_importances_
            self.feature_importance = dict(zip(self.feature_names, importances))

            # 상위 20개 출력
            sorted_features = sorted(
                self.feature_importance.items(),
                key=lambda x: x[1],
                reverse=True
            )

            print("\n상위 20개 중요 Feature:")
            for i, (feature, importance) in enumerate(sorted_features[:20], 1):
                print(f"  {i:2d}. {feature:30s}: {importance:.4f}")

    def predict(self, features: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray]:
        """
        급등 예측

        Args:
            features: Feature DataFrame

        Returns:
            Tuple: (예측 레이블, 예측 확률)
        """
        if self.model is None:
            raise ValueError("모델이 학습되지 않았습니다.")

        # Feature 순서 맞추기
        X = features[self.feature_names]
        X = X.fillna(X.median())

        # 스케일링
        X_scaled = self.scaler.transform(X)

        # 예측
        predictions = self.model.predict(X_scaled)
        probabilities = self.model.predict_proba(X_scaled)[:, 1]

        return predictions, probabilities

    def predict_single(self, ohlcv: pd.DataFrame) -> Dict:
        """
        단일 코인의 급등 가능성 예측

        Args:
            ohlcv: OHLCV DataFrame (최소 100시간)

        Returns:
            Dict: 예측 결과
        """
        if len(ohlcv) < 100:
            return {
                'success': False,
                'error': 'Insufficient data (need at least 100 hours)'
            }

        # Feature 생성
        features_df = self.feature_engineer.create_features(ohlcv)

        if features_df.empty:
            return {
                'success': False,
                'error': 'Failed to create features'
            }

        # 최근 시점 데이터만 사용
        latest_features = features_df.iloc[[-1]]

        # 예측
        try:
            predictions, probabilities = self.predict(latest_features)

            return {
                'success': True,
                'will_surge': bool(predictions[0]),
                'surge_probability': float(probabilities[0]),
                'confidence': 'high' if probabilities[0] > 0.8 or probabilities[0] < 0.2 else
                            'medium' if probabilities[0] > 0.6 or probabilities[0] < 0.4 else 'low'
            }

        except Exception as e:
            return {
                'success': False,
                'error': str(e)
            }

    def save_model(self, filepath: str):
        """모델 저장"""
        os.makedirs(os.path.dirname(filepath), exist_ok=True)

        model_data = {
            'model': self.model,
            'scaler': self.scaler,
            'feature_names': self.feature_names,
            'feature_importance': self.feature_importance,
            'training_metrics': self.training_metrics,
            'model_type': self.model_type
        }

        with open(filepath, 'wb') as f:
            pickle.dump(model_data, f)

        print(f"모델 저장 완료: {filepath}")

    def load_model(self, filepath: str):
        """모델 로드"""
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"모델 파일을 찾을 수 없습니다: {filepath}")

        with open(filepath, 'rb') as f:
            model_data = pickle.load(f)

        self.model = model_data['model']
        self.scaler = model_data['scaler']
        self.feature_names = model_data['feature_names']
        self.feature_importance = model_data.get('feature_importance', {})
        self.training_metrics = model_data.get('training_metrics', {})
        self.model_type = model_data.get('model_type', 'unknown')

        print(f"모델 로드 완료: {filepath}")
        print(f"모델 타입: {self.model_type}")
        print(f"Feature 개수: {len(self.feature_names)}")

        if self.training_metrics:
            test_metrics = self.training_metrics.get('test', {})
            print(f"Test F1-Score: {test_metrics.get('f1_score', 0):.4f}")
