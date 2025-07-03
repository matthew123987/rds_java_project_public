# rds_java_project_public

This code was vibe coded by Gemini 2.5 pro. Do not use in prod.  

It uses spring boot.

It creates one java process which runs the below multi thread:

- java backend
- embedded tomcat website for hosting front end , with embedded javascript and html. 

I successfully ran this code on a EC2 instance by building with maven, with an application load balancer set up to host the website, hosted by route53, using ssl certificate from certificate manager. 

The EC2 connects to RDS, both in private subnet. But the app routes to public subnet on ec2, to get internet access.

Full details of java and aws programs installed on EC2, and logic about how the code works to be added.
