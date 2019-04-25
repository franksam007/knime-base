#!groovy
def BN = BRANCH_NAME == "master" || BRANCH_NAME.startsWith("releases/") ? BRANCH_NAME : "master"

library "knime-pipeline@$BN"

properties([
	parameters([
		stringParam(
			name: 'KNIME_TP_P2',
			defaultValue: '${P2_REPO}/knime-tp/' + env.BRANCH_NAME.replaceAll("/", "%252F") + '/repository/',
			description: 'KNIME Target Platform P2 update site url.'
		),
		stringParam(
			name: 'KNIME_SHARED_P2',
			defaultValue: '${P2_REPO}/knime-shared/'+ env.BRANCH_NAME.replaceAll("/", "%252F") + '/repository/',
			description: 'org.knime.update.shared site url.'
		),
		stringParam(
			name: 'KNIME_CORE_P2',
			defaultValue: '${P2_REPO}/knime-core/'+ env.BRANCH_NAME.replaceAll("/", "%252F") + '/repository/',
			description: 'org.knime.update.core site url.'
		),
		stringParam(
			name: 'KNIME_EXPRESSIONS_P2',
			defaultValue: '${P2_REPO}/knime-expressions/'+ env.BRANCH_NAME.replaceAll("/", "%252F") + '/repository/',
			description: 'org.knime.update.expressions site url.'
		)
	]),

	pipelineTriggers([upstream('knime-expressions/' + env.BRANCH_NAME.replaceAll('/', '%2F'))]),
	buildDiscarder(logRotator(numToKeepStr: '5')),
	disableConcurrentBuilds()
])

node('maven') {
	stage('Checkout Sources') {
		checkout scm
	}

	try{
		stage('Tycho Build') {
			withMavenJarsignerCredentials {
				sh '''
					export PATH="$MVN_CMD_DIR:$PATH"
					export TEMP="${WORKSPACE}/tmp"
					rm -rf "${TEMP}"
					mkdir "${TEMP}"
					mvn -Dmaven.test.failure.ignore=true clean verify
					rm -rf "${TEMP}"
				'''
			}

			// junit '**/target/test-reports/*/TEST-*.xml'
		}
		if (BRANCH_NAME == "master" || BRANCH_NAME.startsWith("releases/") || BRANCH_NAME.startsWith("build/DEVOPS-35")) {
			if (currentBuild.result != 'UNSTABLE') {
				stage('Deploy p2') {
					withMavenJarsignerCredentials {
						sh '''
							export PATH="$MVN_CMD_DIR:$PATH"
							mvn -D skipTests=true install
						'''
					}

					sh '''
						mkdir -p "/var/cache/build_artifacts/${JOB_NAME}/"
						rm -Rf /var/cache/build_artifacts/${JOB_NAME}/*
						cp -a ${WORKSPACE}/org.knime.update.base/target/repository/ /var/cache/build_artifacts/${JOB_NAME}
					'''
				}
			} else {
				echo "==============================================\n" +
					 "| Build unstable, not deploying p2 artifacts.|\n" +
					 "=============================================="
			}
		}
	} catch (ex) {
		currentBuild.result = 'FAILED'
		throw ex
	} finally {
		notifications.notifyBuild(currentBuild.result);
	}
}

/* vim: set ts=4: */
