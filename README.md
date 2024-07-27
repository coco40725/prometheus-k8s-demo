# Prometheus 和 Grafana deploy in k8s super easy Demo
本專案為示範使用 quarkus 搭配 k8s 建立 Prometheus 和 Grafana 簡易監控系統的流程。

## Requirements
- k8s (已經建立好 cluster)
- Quarkus



## Getting Started
### 1. 建立 pod
需要建立的 pod 共有以下三個:
- app: 服務本身
- prometheus: 監控系統，負責收集資料
- grafana: 監控系統的圖像化 UI，將資料圖像化

### 2. 設定 app, prometheus, grafana 三個 pod ingress
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ingress-nginx-refactor
  namespace: default
spec:
  ingressClassName: nginx
  rules:
    - host: www.myexample.com.tw
      http:
        paths:
        - backend:
            service:
              name: prometheus-k8s-demo
              port:
                number: 8080
          path: /prometheus-k8s-demo/
          pathType: Prefix
        - backend:
            service:
              name: prometheus-service
              port:
                number: 9090
          path: /prometheus/
          pathType: Prefix
        - backend:
            service:
              name: grafana-service
              port:
                number: 3000
          path: /grafana/
          pathType: Prefix
```

### 3. 設定 app k8s yaml
- 建立 Deployment 與 Service yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  namespace: default
  name: prometheus-k8s-demo
  labels:
    app: prometheus-k8s-demo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: prometheus-k8s-demo
  template:
    metadata:
      name: prometheus-k8s-demo
      labels:
        app: prometheus-k8s-demo
    spec:
      containers:
        - name: prometheus-k8s-demo
          image: asia-east1-docker.pkg.dev/my-project-id/prometheus-k8s-demo/test-image:latest-SNAPSHOT
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
              protocol: TCP
          resources:
            requests:
              memory: "250Mi"
              cpu: "100m"
            limits:
              memory: "500Mi"
              cpu: "500m"
      restartPolicy: Always

---
apiVersion: v1
kind: Service
metadata:
  name: prometheus-k8s-demo
  namespace: default
  labels:
    app: prometheus-k8s-demo
spec:
  type: NodePort
  ports:
    - port: 8080
      targetPort: 8080
      protocol: TCP
      name: http
  selector:
    app: prometheus-k8s-demo
```

### 4. 設定 prometheus k8s yaml
- 建立 prometheus 的 config yaml
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: default
data:
  prometheus.yaml: |
    global:
      scrape_interval: 5s # Server 抓取頻率
      evaluation_interval: 5s # Server 評估頻率
      external_labels:
        monitor: "my-monitor"
    rule_files: #如何整併數據或建立告警條件
      - "alert.rules"
      - "nginx.rules"
    scrape_configs: # 去哪邊蒐集資料
      - job_name: "prometheus"
        metrics_path: "prometheus/metrics"
        scheme: https
        static_configs:
          - targets: ["www.myexample.com.tw"] 
            
      - job_name: "prometheus-k8s-demo"
        metrics_path: "prometheus-k8s-demo/q/metrics"
        scheme: https
        static_configs:
          - targets: ["www.myexample.com.tw"]            
```

- 建立 prometheus 的 Deployment 與 Service yaml
這裡需要特別說明，兩個參數: `--web.route-prefix` 與 `--web.external-url`，
  - `--web.route-prefix` Prefix for the internal routes of web endpoints. Defaults to `/`.
  - `--web.external-url` 	The URL under which Prometheus is externally reachable (for example, if Prometheus is served via a reverse proxy). Used for generating relative and absolute links back to Prometheus itself. If the URL has a path portion, it will be used to prefix all HTTP endpoints served by Prometheus. If omitted, relevant URL components will be derived automatically.

  這兩個參數是為了讓 prometheus 可以正確的導向到 ingress 的路徑並且暴露出來 (https://www.myexample.com.tw/prometheus)。
因為 prometheus 本身預設 prefix 為 `/`，因此，如果你沒有特別設定這`--web.route-prefix`變數，那麼會導致 prometheus 一直導向到 `/`，而不是 `/prometheus`，例如:
當你沒有設定 `--web.route-prefix` 變數時，你會看到 prometheus 一直自動幫你導向到 https://www.myexample.com.tw/login ，而不是 https://www.myexample.com.tw/prometheus/login 。

- 
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus-server
  namespace: default
  labels:
    app: prometheus-server
spec:
  replicas: 1
  selector:
    matchLabels:
      app: prometheus-server
  template:
    metadata:
      name: prometheus-server
      labels:
        app: prometheus-server
    spec:
      containers:
        - name: prometheus-server
          image: prom/prometheus
          imagePullPolicy: Always
          ports:
            - containerPort: 9090
          args:
            - --config.file=/etc/prometheus/prometheus.yml
            - --web.route-prefix=/prometheus
            - --web.external-url=https://www.myexample.com.tw/prometheus


          volumeMounts:
            - name: config-volume
              mountPath: /etc/prometheus
              readOnly: true



      volumes:
        - name: config-volume
          configMap:
            name: prometheus-config
            items:
              - key: prometheus.yaml
                path: prometheus.yml
      restartPolicy: Always
---
apiVersion: v1
kind: Service
metadata:
  name: prometheus-service
  namespace: default
spec:
  selector:
    app: prometheus-server #選擇的 app=prometheus-server 標籤的 Pod
  ports:
    - protocol: TCP
      port: 9090
      targetPort: 9090
      name: http
  type: NodePort

```

### 5. 設定 grafana k8s yaml
- 建立 prometheus 的 config yaml
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-config
data:
  grafana.ini: |
    #https://github.com/grafana/grafana/blob/main/conf/defaults.ini
    #################################### Server ##############################
    [server]
    # Protocol (http, https, h2, socket)
    protocol = http
    
    # Minimum TLS version allowed. By default, this value is empty. Accepted values are: TLS1.2, TLS1.3. If nothing is set TLS1.2 would be taken
    min_tls_version = ""
    
    # The ip address to bind to, empty will bind to all interfaces
    http_addr =
    
    # The http port to use
    http_port = 3000
    
    # The public facing domain name used to access grafana from a browser
    domain = localhost
    
    # Redirect to correct domain if host header does not match domain
    # Prevents DNS rebinding attacks
    enforce_domain = false
    
    serve_from_sub_path = true
    
    # The full public facing url
    root_url = %(protocol)s://%(domain)s:%(http_port)s/grafana

```

- 建立 grafana 的 Deployment 與 Service yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana-server
  labels:
    app: grafana-server
spec:
  replicas: 1
  selector:
    matchLabels:
      app: grafana-server
  template:
    metadata:
      name: grafana-server
      labels:
        app: grafana-server
    spec:
      securityContext:
        runAsUser: 0 # 用 user = root 來執行, 這樣才能存取 /var/lib/grafana
      containers:
        - name: grafana-server
          image: grafana/grafana-enterprise
          imagePullPolicy: Always
          ports:
            - containerPort: 3000
              protocol: TCP
          volumeMounts:
            - mountPath: /var/lib/grafana
              name: storage
            - mountPath: /etc/grafana
              name: grafana-config

          env:
          - name: GF_SECURITY_ADMIN_USER
            value: xxx
          - name: GF_SECURITY_ADMIN_PASSWORD
            value: xxx

      restartPolicy: Always

      volumes:
        - name: storage
          persistentVolumeClaim:
            claimName: grafana-pvc

        - name: grafana-config
          configMap:
            name: grafana-config
            items:
              - key: grafana.ini
                path: grafana.ini

---
apiVersion: v1
kind: Service
metadata:
  name: grafana-service
  namespace: default
spec:
  selector:
    app: grafana-server
  ports:
    - protocol: TCP
      port: 3000
      targetPort: 3000
      name: http
  type: NodePort

```
- 建立 grafana 的 PVC yaml
```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: grafana-pv
spec:
  capacity:
    storage: 1Gi
  accessModes:
    - ReadWriteOnce
  volumeMode: Filesystem
  hostPath:
    path: /tmp/grafana
    type: DirectoryOrCreate
  persistentVolumeReclaimPolicy: Retain


---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: grafana-pvc
spec:
  storageClassName: ""
  volumeName: grafana-pv
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
```

