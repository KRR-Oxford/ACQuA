<!-- TITLE -->
<p align="center">

<h1 align="center">&middot; ACQuA &middot;</h1>

<p align="center">
A hybrid architecture for conjunctive query answering overover OWL 2 DL.
<br/>
<a href="#">Read the thesis</a>
&middot;
<a href="#">Read the journal paper</a>
&middot;
<a href="https://github.com/KRR-Oxford/ACQuA/issues">Report bug</a>
<br/><br/>
<a href="https://github.com/KRR-Oxford/ACQuA/releases/latest">
    <img src="https://img.shields.io/github/release/KRR-Oxford/RSAComb.svg?style=for-the-badge" alt="Release badge">
</a>
<a href="https://github.com/KRR-Oxford/ACQuA/issues">
    <img src="https://img.shields.io/github/issues/KRR-Oxford/RSAComb.svg?style=for-the-badge" alt="Issues badge">
</a>
<!-- <a href="https://github.com/KRR-Oxford/ACQuA/actions"> -->
<!--     <img src="https://img.shields.io/github/workflow/status/KRR-Oxford/RSAComb/Scala%20CI/develop?label=TESTS&style=for-the-badge" alt="GitHub Actions badge"> -->
<!-- </a> -->
<a href="LICENSE">
    <img src="https://img.shields.io/github/license/KRR-Oxford/ACQuA.svg?style=for-the-badge" alt="License badge">
</a>
<a href="https://doi.org/10.5281/zenodo.5047810">
    <img src="https://img.shields.io/badge/DOI-10.5281/zenodo.5047810-blue?style=for-the-badge" alt="DOI badge">
</a>
</p>

</p>

## About

ACQuA is a hybrid query answering framework that combines black-box services to provide a CQ answering service for OWL.
Specifically, it combines scalable CQ answering services for tractable languages with a CQ answering service for a more expressive language approaching the full OWL 2.
If the query can be fully answered by one of the tractable services, then that service is used.
Otherwise the tractable services are used to compute lower and upper bound approximations, taking the union of the lower bounds and the intersection of the upper bounds.
If the bounds don’t coincide, then the “gap” answers are checked using the “full” service.

This reference implementation combines [RSAComb], [PAGOdA], and [HermiT], but these tools can be potentially subtituted or augmented with more capable ones to improve the overall performance of the system.

> ACQuA is still in its preliminary stage of development and might contain bugs.

## Preliminaries

RSAComb uses a recent version of [RDFox] under the hood to offload part of the computation.

In order to run ACQuA you need to have [RDFox](https://www.oxfordsemantic.tech/product) available in your system, along with *a valid license*.
RDFox is proprietary software and as such we are not able to distribute it along with our code.
This software has been developed and tested with RDFox v5.5

### Installing RDFox

We refer to the [official documentation](https://docs.oxfordsemantic.tech/getting-started.html#getting-started) for a step-by-step guide on how to setup RDFox on your personal machine.
In particular, you will need to know the path to the RDFox Java API (usually called `JRDFox.jar`) that comes with the distribution.

Alternatively, run the following commands (on a Linux x86 machine) from the root of the project to install RDFox locally.
Download links for other versions, operating systems, and architectures can be found [here](https://www.oxfordsemantic.tech/downloads).

```{.bash}
mkdir -p lib && pushd lib
wget https://rdfox-distribution.s3.eu-west-2.amazonaws.com/release/v5.5/RDFox-linux-x86_64-5.5.zip
unzip RDFox-linux-x86_64-5.2.1.zip
ln -s RDFox-linux-x86_64-5.2.1.zip/lib/JRDFox.jar
popd
```

### Providing an RDFox license

The [documentation](https://docs.oxfordsemantic.tech/features-and-requirements.html#license-key), describes several ways to provide the license to RDFox.

One easy way is to put your license key in a file `RDFox.lic` in `$HOME/.RDFox/`, with adequate read permissions for the user executing the program.


## Using the software

`TODO`

### Running tests

`TODO`

```
mvn compile
mvn package
java -cp ... uk.ac.ox.cs.acqua.Acqua [OPTIONs]
```

## References

[1] Horridge, Matthew and Bechhofer, Sean.
    *The OWL API: A Java API for OWL Ontologies*.
    Semantic Web Journal 2(1), Special Issue on Semantic Web Tools and Systems, pp. 11-21, 2011.

## Acknowledgements

- OWLAPI [[2]](#references)
- [RDFox](https://www.oxfordsemantic.tech/product)
- [PAGOdA]()

## Credits

- Federico Igne
- Stefano Germano
- Ian Horrocks (*Scientific Supervisor*)

From the [Knowledge Representation and Reasoning research group](https://www.cs.ox.ac.uk/isg/krr/) in the [Department of Computer Science](https://www.cs.ox.ac.uk/) of the [University of Oxford](https://www.ox.ac.uk/).

## License

This project is licensed under the [Apache License 2.0](LICENSE).

## TODOs

- [ ] Better integration between ACQuA, RDFox and PAGOdA configuration parameters.
