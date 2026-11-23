pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        skipDefaultCheckout()
    }

    environment {
        AWS_REGION = 'ap-northeast-2'
        ECR_REGISTRY = '504034151385.dkr.ecr.ap-northeast-2.amazonaws.com'
        ECR_REPOSITORY = 'quantflow-backend'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                sh 'python3 scripts/test-toss-integration.py --no-daemon'
            }
        }

        stage('Build JAR') {
            steps {
                sh './gradlew --no-daemon bootJar'
                archiveArtifacts artifacts: 'build/libs/*.jar'
            }
        }

        stage('Build and Push Image') {
            steps {
                    sh '''#!/bin/bash
            set -euo pipefail

            commit=$(git rev-parse --short=12 HEAD)
            image="${ECR_REGISTRY}/${ECR_REPOSITORY}:${BUILD_NUMBER}-${commit}"

            temp_dir=$(mktemp -d)
            trap 'rm -rf -- "$temp_dir"' EXIT
            mkdir -p "$temp_dir/context" "$temp_dir/docker-config"

            cp Dockerfile "$temp_dir/context/"
            cp build/libs/quantflow-backend-0.0.1-SNAPSHOT.jar "$temp_dir/context/app.jar"
            docker build --tag "$image" "$temp_dir/context"

            export DOCKER_CONFIG="$temp_dir/docker-config"
            aws ecr get-login-password --region "$AWS_REGION" |
                docker login --username AWS --password-stdin "$ECR_REGISTRY"

            docker push "$image"
            '''
                }
        }
    }
}
