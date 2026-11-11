#!/usr/bin/env bash
set -eu

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y python3

sudo -u vagrant test -r /vagrant/app.py
sudo -u vagrant test -w /vagrant/data
sudo -u vagrant test -w /vagrant/data/reservations.json

cat > /etc/default/reservation-api <<'EOF'
APP_BIND_ADDRESS=0.0.0.0
APP_PORT=8080
RESERVATION_DATA_FILE=/vagrant/data/reservations.json
EOF

cat > /etc/systemd/system/reservation-api.service <<'EOF'
[Unit]
Description=Club reservation API
After=network.target

[Service]
Type=simple
User=vagrant
Group=vagrant
WorkingDirectory=/vagrant
EnvironmentFile=/etc/default/reservation-api
Environment=PYTHONUNBUFFERED=1
ExecStart=/usr/bin/python3 /vagrant/app.py
Restart=on-failure
RestartSec=2

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable reservation-api.service
systemctl restart reservation-api.service
systemctl --no-pager --full status reservation-api.service
