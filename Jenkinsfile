pipeline {
    agent {
        kubernetes {
            label 'maven-agent'
            defaultContainer 'maven'
        }
    }

    // Allineiamo le variabili d'ambiente con quelle del tuo application.yml
    environment {
        SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/robofleet_db'
        DB_USERNAME           = 'admin'
        DB_PASSWORD           = 'admin'
    }

    stages {
        stage('Build Maven') {
            steps {
                container('maven') {
                    sh 'mvn clean install -DskipTests'
                }
            }
        }
    }
}