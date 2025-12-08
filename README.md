# secu-project

- 62294 - SAEED Anees
- 63009 - EL Hichou Abderrahman
- 60287 - Ilias Abouchouar

--- 

This project uses HTTPS on `https://localhost` via Nginx.  

Nginx expects the following files:

- `certs/localhost.pem`
- `certs/localhost-key.pem`

To generate certificates :
run from the project root

```bash
./generate-localhost-cert.sh 
