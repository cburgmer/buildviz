var graphDescription = function (d3) {
    var module = {};

    var createBlock = function (text, headline) {
        var block = d3.select(document.createElement('div'));
        if (headline) {
            block
                .append('h4')
                .text(headline);
        }
        block
            .append('p')
            .text(text);
        return block.node();
    };

    module.create = function (params) {
        var container = d3.select(document.createElement('div'))
                .attr('class', 'graphDescription')
                .text('?'),
            section = container
                .append('section');

        [{text: params.description},
         {headline: 'Answer to', text: params.answer},
         {headline: 'Legend', text: params.legend}].forEach(function (block) {
             if (block.text) {
                 section.node().appendChild(createBlock(block.text, block.headline));
             }
         });


        return {
            widget: container.node()
        };
    };

    return module;
}(d3);
