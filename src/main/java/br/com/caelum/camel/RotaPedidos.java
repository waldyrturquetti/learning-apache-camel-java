package br.com.caelum.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;

public class RotaPedidos {

	public static void main(String[] args) throws Exception {

		CamelContext context = new DefaultCamelContext();
		context.addRoutes(new RouteBuilder() {

			@Override
			public void configure() throws Exception {

				from("file:pedidos?delay=5s&noop=true")
						.routeId("rota-pedidos")
						.multicast()
					.to("direct:http")
					.to("direct:soap");

				from("direct:http")
					.routeId("rota-http")
						.setProperty("pedidoId", xpath("/pedido/id/text()"))
						.setProperty("clienteId", xpath("/pedido/pagamento/email-titular/text()"))
						.split().xpath("/pedido/itens/item")
						.filter().xpath("/item/formato[text()='EBOOK']")
						.setProperty("ebookId", xpath("/item/livro/codigo/text()"))
						.marshal().xmljson()
						.setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
						.setHeader(Exchange.HTTP_QUERY,
								simple("ebookId=${property.ebookId}&pedidoId=${property.pedidoId}&clienteId=${property.clienteId}"))
					.to("http4://localhost:8080/webservices/ebook/item");

				from("direct:soap")
					.routeId("rota-soap")
						.to("xslt:pedido-para-soap.xslt")
						.log("Resultado do Template: ${body}")
						.setHeader(Exchange.CONTENT_TYPE, constant("text/xml"))
					.to("http4://localhost:8080/webservices/financeiro");

				from("direct:velocity")
						.setHeader("data", constant("8/12/2015"))
						.to("velocity:template.vm")
								.log("${body}");
			}
		});

		context.start();

		ProducerTemplate producer = context.createProducerTemplate();
		producer.sendBody("direct:velocity", "Apache Camel rocks!!!");

		Thread.sleep(2000);
		context.stop();
	}	
}
