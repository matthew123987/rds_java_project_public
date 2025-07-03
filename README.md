# rds_java_project_public


It uses spring boot.

It creates one java process which runs the below multi thread:

- java backend
- embedded tomcat website for hosting front end , with embedded javascript and html. 

I successfully ran this code on a EC2 instance by building with maven, with an application load balancer set up to host the website, hosted by route53, using ssl certificate from certificate manager. 

The EC2 connects to RDS, both in private subnet. But the app routes to public subnet on ec2, to get internet access.

Free tier EC2 was used while building the project, but intermittent 100% CPU usage was reached, breaking the app. Upgraded to t3.large non free tier, to fix the CPU issues. Probably the vibe coding causing this issue, and refactoring will improve this.

Also, need to add the prompts that I gave gemini when creating this through gemini gem. Gem was crucial for this, to prevent gemini from constantly trying to refactor and completely breaking the app. Gemini kept forgetting the instructions to not refactor, and gem was the only way to stop it permanently. This project would have taken 2x as long without that, and would have majorly ruined the gemini experience.

To do list:

- Full details of java and aws programs installed on EC2
- Logic about how the code works to be added.
- Commands used to quickly push, pull and build
- Finish to do list
