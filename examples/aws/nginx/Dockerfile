FROM alpine

RUN apk add --no-cache nginx && \
    install -d -m0755 -o nginx -g nginx /var/log/nginx && \
    rm -rf /var/cache/apk/*

EXPOSE 8080
USER nginx

ADD nginx.conf /var/lib/nginx/

ENTRYPOINT ["nginx"]

CMD [ "-c", "/var/lib/nginx/nginx.conf" ]
