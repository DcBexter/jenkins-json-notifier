Jenkins JSON Notifier
====================

Jenkins plugin sending a POST request after a successful Freestyle build, with a configurable JSON payload. The endpoint must return **HTTP 204**.

The default payload contains the job name, build number, result and URL, plus Git branch and commit SHA when those environment variables exist.
 
Being a configurable [Groovy template](https://groovy-lang.org/templating.html), it can include other Jenkins, job or build details. Custom templates require administrator approval under **Manage Jenkins > In-process Script Approval**, including templates saved before upgrading. They execute trusted code on the controller; see [security notes](SECURITY.md).

![Post-build action with JSON payload submitted as POST request](screenshots/jenkins-notify-plugin.png "Post-build action with JSON payload submitted as POST request")

JSON payload is rendered as a [Groovy template](http://groovy.codehaus.org/Groovy+Templates), having the following variables in scope:
 
* **`jenkins`** - instance of [`jenkins.model.Jenkins`](https://javadoc.jenkins.io/jenkins/model/Jenkins.html)
* **`build`** - instance of [`hudson.model.AbstractBuild`](https://javadoc.jenkins.io/hudson/model/AbstractBuild.html)
* **`env`** - instance of [`hudson.EnvVars`](https://javadoc.jenkins.io/hudson/EnvVars.html) corresponding to the current build process

Example of a received notification (historical screenshot):

![RequestBin for a JSON payload](screenshots/request-bin.png "RequestBin for a JSON payload")

In addition, **`json( Object )`** helper function is available, rendering any `Object` provided as JSON.

For example:

    {
      "items":       ${ json( jenkins.allItems ) },
      "computers":   ${ json( jenkins.computers.collect{ it.displayName }) },
      "moduleRoots": ${ json( build.moduleRoots )},
      "artifacts":   ${ json( build.artifacts )},
      "env":         ${ json( env ) },
      "properties":  ${ json( [ system: System.properties.keySet(), env: env.keySet() ]) }
    }

### Building and installing the plugin

Requires **Jenkins 2.568.3 or newer**, **Java 21 or 25**, and **Maven 3.9.6 or newer**.

    mvn -B -ntp clean verify

Upload `target/notify-plugin.hpi` under **Manage Jenkins > Plugins > Advanced settings > Deploy Plugin**, then restart Jenkins. Install the declared dependencies when prompted. The internal plugin ID remains `notify-plugin`, so existing job configuration keeps its identity. Pipeline jobs are not supported.

CI tests the minimum Jenkins version, current LTS and current weekly on Java 21, plus current LTS on Java 25. Renovate opens dependency update PRs; minor, patch, pin and digest updates may merge automatically only after required checks pass. Major updates remain for manual review.

Originally based on [cloudnative/jenkins-notify-plugin](https://github.com/cloudnative/jenkins-notify-plugin). Apache-2.0 license retained.
