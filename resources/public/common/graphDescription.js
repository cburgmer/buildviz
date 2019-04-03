const graphDescription = function (d3) {
    const module = {};

    const githubWikiLink = function (question) {
        const baseUrl = 'https://github.com/cburgmer/buildviz/wiki/Questions';

        return baseUrl + '#' + question.replace(/ /g, '-').replace(/\?/g, '').toLowerCase();
    };

    const answerToListWidget = function (answersTo) {
        const ul = d3.select(document.createElement('ul'));
        answersTo.forEach(function (answerTo) {
            ul.append('li')
                .append('a')
                .attr('href', githubWikiLink(answerTo))
                .text(answerTo);
        });
        return ul.node();
    };

    const textWidget = function (text) {
        const p = d3.select(document.createElement('p'));
        p.text(text);
        return p.node();
    };

    const csvLinkWidget = function (link) {
        const h4 = d3.select(document.createElement('h4'));
        h4.append('a')
            .attr('href', link)
            .text('Source as CSV');
        return h4.node();
    };

    const createBlock = function (content, widget, headline) {
        const block = d3.select(document.createElement('div'));
        if (headline) {
            block
                .append('h4')
                .text(headline);
        }
        block.node().appendChild(widget(content));
        return block.node();
    };

    module.create = function (params) {
        const container = d3.select(document.createElement('div'))
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
