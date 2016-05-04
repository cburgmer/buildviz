var graphDescription = function (d3) {
    var module = {};

    var githubWikiLink = function (question) {
        var baseUrl = 'https://github.com/cburgmer/buildviz/wiki/Questions';

        return baseUrl + '#' + question.replace(/ /g, '-').replace(/\?/g, '').toLowerCase();
    };

    var answerToListWidget = function (answersTo) {
        var ul = d3.select(document.createElement('ul'));
        answersTo.forEach(function (answerTo) {
            ul.append('li')
                .append('a')
                .attr('href', githubWikiLink(answerTo))
                .text(answerTo);
        });
        return ul.node();
    };

    var textWidget = function (text) {
        var p = d3.select(document.createElement('p'));
        p.text(text);
        return p.node();
    };

    var csvLinkWidget = function (link) {
        var h4 = d3.select(document.createElement('h4'));
        h4.append('a')
            .attr('href', link)
            .text('Source as CSV');
        return h4.node();
    };

    var createBlock = function (content, widget, headline) {
        var block = d3.select(document.createElement('div'));
        if (headline) {
            block
                .append('h4')
                .text(headline);
        }
        block.node().appendChild(widget(content));
        return block.node();
    };

    module.create = function (params) {
        var container = d3.select(document.createElement('div'))
                .attr('class', 'graphDescription')
                .text('?'),
            section = container
                .append('section');

        [
            {content: params.description, widget: textWidget},
            {headline: 'Answer to', content: params.answer, widget: answerToListWidget},
            {headline: 'Legend', content: params.legend, widget: textWidget},
            {content: params.csvSource, widget: csvLinkWidget}
        ].forEach(function (block) {
             if (block.content) {
                 section.node().appendChild(createBlock(block.content, block.widget, block.headline));
             }
         });

        return {
            widget: container.node()
        };
    };

    return module;
}(d3);
