# Security

Report vulnerabilities privately using this repository's **Security > Report a vulnerability** form.

Only the current maintained branch is supported. Custom notification templates run
Groovy on the Jenkins controller and need administrator approval in **Manage Jenkins >
In-process Script Approval**. Review templates as trusted code; the `jenkins`, `build`
and `env` bindings can access sensitive data. Approve again after template changes.

Use trusted HTTPS notification endpoints. The plugin does not add credentials to
requests. Do not put secrets into payloads unless the destination is authorized to
receive them. Failed notifications must not log payloads or webhook tokens.
